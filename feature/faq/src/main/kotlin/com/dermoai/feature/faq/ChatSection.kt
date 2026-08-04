package com.dermoai.feature.faq

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dermoai.core.ui.components.NeuButton
import com.dermoai.core.ui.components.NeuIconButton
import com.dermoai.core.ui.components.NeuSurface
import com.dermoai.core.ui.components.NeuSurfaceStyle
import com.dermoai.core.ui.theme.DermoColors
import com.dermoai.feature.faq.data.ChatErrorKind
import com.dermoai.feature.faq.data.ChatMessage
import com.dermoai.feature.faq.data.Role

/**
 * AI assistant chat (DeepSeek chat-completions, user-supplied API key).
 * Embedded in the FAQ screen under the "AI assistant" tab.
 */
@Composable
fun ChatSection(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsState()
    val sending by viewModel.sending.collectAsState()
    val error by viewModel.error.collectAsState()
    val hasApiKey by viewModel.hasApiKey.collectAsState()

    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val itemCount = messages.size + if (sending) 1 else 0

    // Auto-scroll to the newest message / typing indicator.
    LaunchedEffect(itemCount) {
        if (itemCount > 0) runCatching { listState.animateScrollToItem(itemCount - 1) }
    }

    if (!hasApiKey) {
        NoApiKeyState(modifier, onOpenSettings)
        return
    }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        // Header: model + clear conversation
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.chat_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { viewModel.clear() }) {
                Text(stringResource(R.string.chat_clear), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (messages.isEmpty() && !sending) {
            WelcomeState(Modifier.weight(1f), onSuggestion = { viewModel.send(it) })
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages) { message -> MessageBubble(message) }
                if (sending) {
                    item(key = "__typing__") { TypingBubble() }
                }
            }
        }

        error?.let { e ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(errorMessageRes(e.kind)),
                    style = MaterialTheme.typography.bodySmall,
                    color = DermoColors.CoralText,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { viewModel.retryLast() }) {
                    Text(stringResource(R.string.chat_retry), color = DermoColors.TealAccent)
                }
            }
        }

        // Input row
        Row(
            Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding(),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(stringResource(R.string.chat_input_hint)) },
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            NeuIconButton(
                onClick = {
                    viewModel.send(input)
                    input = ""
                },
                enabled = input.isNotBlank() && !sending,
                icon = Icons.AutoMirrored.Outlined.Send,
                contentDescription = stringResource(R.string.chat_send),
                containerColor = DermoColors.Teal,
                contentColor = Color.White,
            )
        }
    }
}

@Composable
private fun NoApiKeyState(modifier: Modifier, onOpenSettings: () -> Unit) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.SmartToy, null, Modifier.width(40.dp).height(40.dp), tint = DermoColors.TealAccent)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.chat_no_key_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.chat_no_key_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            NeuButton(
                onClick = onOpenSettings,
                containerColor = DermoColors.Teal,
                contentColor = Color.White,
            ) {
                Text(stringResource(R.string.chat_open_settings))
            }
        }
    }
}

@Composable
private fun WelcomeState(modifier: Modifier, onSuggestion: (String) -> Unit) {
    val suggestions = listOf(
        R.string.chat_suggestion_scan_results,
        R.string.chat_suggestion_privacy,
        R.string.chat_suggestion_acne,
        R.string.chat_suggestion_sunscreen,
        R.string.chat_suggestion_mole,
        R.string.chat_suggestion_dermatologist,
    )
    // Resolve at composition time: onClick lambdas are not @Composable.
    val suggestionTexts = suggestions.map { stringResource(it) }
    Column(modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text(stringResource(R.string.chat_welcome_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.chat_welcome_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        suggestionTexts.forEach { text ->
            NeuSurface(
                style = NeuSurfaceStyle.Inset,
                shape = RoundedCornerShape(14.dp),
                onClick = { onSuggestion(text) },
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == Role.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        NeuSurface(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.82f else 0.94f),
            style = if (isUser) NeuSurfaceStyle.Raised else NeuSurfaceStyle.Raised,
            color = if (isUser) DermoColors.Teal else MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(14.dp),
            )
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        NeuSurface(
            modifier = Modifier.fillMaxWidth(0.6f),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = stringResource(R.string.chat_typing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(14.dp),
            )
        }
    }
}

@Composable
private fun errorMessageRes(kind: ChatErrorKind): Int = when (kind) {
    ChatErrorKind.MISSING_KEY -> R.string.chat_error_missing_key
    ChatErrorKind.INVALID_KEY -> R.string.chat_error_invalid_key
    ChatErrorKind.INSUFFICIENT_BALANCE -> R.string.chat_error_insufficient_balance
    ChatErrorKind.RATE_LIMITED -> R.string.chat_error_rate_limited
    ChatErrorKind.SERVER -> R.string.chat_error_server
    ChatErrorKind.NETWORK -> R.string.chat_error_network
    ChatErrorKind.EMPTY_RESPONSE -> R.string.chat_error_empty
    ChatErrorKind.UNKNOWN -> R.string.chat_error_unknown
}
