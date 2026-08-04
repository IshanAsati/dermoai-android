package com.dermoai.feature.faq

import com.dermoai.feature.faq.data.ChatErrorKind
import com.dermoai.feature.faq.data.ChatException
import com.dermoai.feature.faq.data.ChatMessage
import com.dermoai.feature.faq.data.ChatRepository
import com.dermoai.feature.faq.data.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeChatRepository(
        private val failWith: ChatErrorKind? = null,
        private val reply: String = "Assistant reply",
    ) : ChatRepository {
        val hasKey = MutableStateFlow(true)
        override val hasApiKey: Flow<Boolean> = hasKey

        var lastMessages: List<ChatMessage>? = null

        override suspend fun sendChat(messages: List<ChatMessage>): ChatMessage {
            lastMessages = messages
            failWith?.let { throw ChatException(it) }
            return ChatMessage(Role.ASSISTANT, reply)
        }
    }

    @Test
    fun `send trims input, appends user message then assistant reply`() = runTest(dispatcher.scheduler) {
        val vm = ChatViewModel(FakeChatRepository())

        vm.send("  What causes acne?  ")
        dispatcher.scheduler.advanceUntilIdle()

        val messages = vm.messages.value
        assertEquals(2, messages.size)
        assertEquals(Role.USER, messages[0].role)
        assertEquals("What causes acne?", messages[0].content)
        assertEquals(Role.ASSISTANT, messages[1].role)
        assertEquals("Assistant reply", messages[1].content)
        assertFalse(vm.sending.value)
        assertNull(vm.error.value)
    }

    @Test
    fun `conversation history is passed to the repository`() = runTest(dispatcher.scheduler) {
        val repo = FakeChatRepository()
        val vm = ChatViewModel(repo)

        vm.send("First")
        dispatcher.scheduler.advanceUntilIdle()
        vm.send("Second")
        dispatcher.scheduler.advanceUntilIdle()

        val sent = repo.lastMessages
        assertEquals(3, sent?.size) // user, assistant, user
        assertEquals("Second", sent?.last()?.content)
    }

    @Test
    fun `blank input is ignored`() = runTest(dispatcher.scheduler) {
        val vm = ChatViewModel(FakeChatRepository())

        vm.send("   ")
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.messages.value.isEmpty())
    }

    @Test
    fun `repository failure sets error and keeps user message`() = runTest(dispatcher.scheduler) {
        val vm = ChatViewModel(FakeChatRepository(failWith = ChatErrorKind.RATE_LIMITED))

        vm.send("Hello")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ChatErrorKind.RATE_LIMITED, vm.error.value?.kind)
        assertEquals(1, vm.messages.value.size)
        assertEquals(Role.USER, vm.messages.value[0].role)
        assertFalse(vm.sending.value)
    }

    @Test
    fun `retryLast re-sends the last user message`() = runTest(dispatcher.scheduler) {
        val vm = ChatViewModel(FakeChatRepository(reply = "Retried"))

        vm.send("Will this work?")
        dispatcher.scheduler.advanceUntilIdle()
        vm.retryLast()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(4, vm.messages.value.size) // user, assistant, user, assistant
        assertEquals("Retried", vm.messages.value.last().content)
        assertNull(vm.error.value)
    }

    @Test
    fun `unknown exception maps to UNKNOWN error`() = runTest(dispatcher.scheduler) {
        val throwing = object : ChatRepository {
            override val hasApiKey: Flow<Boolean> = MutableStateFlow(true)
            override suspend fun sendChat(messages: List<ChatMessage>): ChatMessage =
                throw RuntimeException("boom")
        }
        val vm = ChatViewModel(throwing)

        vm.send("Hi")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ChatErrorKind.UNKNOWN, vm.error.value?.kind)
        assertFalse(vm.sending.value)
    }

    @Test
    fun `clear resets conversation and error`() = runTest(dispatcher.scheduler) {
        val vm = ChatViewModel(FakeChatRepository(failWith = ChatErrorKind.NETWORK))

        vm.send("Hi")
        dispatcher.scheduler.advanceUntilIdle()
        vm.clear()

        assertTrue(vm.messages.value.isEmpty())
        assertNull(vm.error.value)
        assertFalse(vm.sending.value)
    }

    @Test
    fun `hasApiKey reflects the repository flow`() = runTest(dispatcher.scheduler) {
        val repo = FakeChatRepository()
        val vm = ChatViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.hasApiKey.value)

        repo.hasKey.value = false
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.hasApiKey.value)
    }
}
