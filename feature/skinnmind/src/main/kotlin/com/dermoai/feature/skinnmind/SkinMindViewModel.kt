package com.dermoai.feature.skinnmind

import android.media.MediaRecorder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dermoai.core.database.dao.DailyCheckInDao
import com.dermoai.core.database.entity.DailyCheckInEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class CheckInFormState(
    val skinFeel: Int = 3,
    val itchDiscomfort: Int = 0,
    val sleepQuality: Int = 3,
    val stressLevel: Int = 3,
    val newProductUsed: Boolean = false,
    val newProductNote: String = "",
    val notes: String = "",
)

@HiltViewModel
class SkinMindViewModel @Inject constructor(
    private val checkInDao: DailyCheckInDao,
) : ViewModel() {

    private val _todayCompleted = MutableStateFlow(false)
    val todayCompleted: StateFlow<Boolean> = _todayCompleted.asStateFlow()

    private val _streak = MutableStateFlow(0)
    val streak: StateFlow<Int> = _streak.asStateFlow()

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    /** Set when the mic permission is missing or the recorder fails to start. */
    private val _recordingError = MutableStateFlow(false)
    val recordingError: StateFlow<Boolean> = _recordingError.asStateFlow()

    val formState = MutableStateFlow(CheckInFormState())

    private val _submitted = MutableStateFlow(false)
    val submitted: StateFlow<Boolean> = _submitted.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }

    fun refresh(userId: String) {
        viewModelScope.launch {
            val today = dateFormat.format(Date())
            val completedToday = checkInDao.getByDate(userId, today) != null
            _todayCompleted.value = completedToday
            // A new day (or cleared data) must clear the celebration so the form shows again.
            if (!completedToday) _submitted.value = false
            val all = checkInDao.observeByUserId(userId).first()
            _streak.value = computeStreak(all)
        }
    }

    fun updateSkinFeel(value: Int) { formState.value = formState.value.copy(skinFeel = value) }
    fun updateItch(value: Int) { formState.value = formState.value.copy(itchDiscomfort = value) }
    fun updateSleep(value: Int) { formState.value = formState.value.copy(sleepQuality = value) }
    fun updateStress(value: Int) { formState.value = formState.value.copy(stressLevel = value) }
    fun updateNewProduct(value: Boolean) { formState.value = formState.value.copy(newProductUsed = value) }
    fun updateNewProductNote(value: String) { formState.value = formState.value.copy(newProductNote = value) }
    fun updateNotes(value: String) { formState.value = formState.value.copy(notes = value) }

    fun submit(userId: String) {
        viewModelScope.launch {
            val fs = formState.value
            val today = dateFormat.format(Date())
            val now = System.currentTimeMillis()
            checkInDao.upsert(
                DailyCheckInEntity(
                    id = "checkin_${UUID.randomUUID()}",
                    userId = userId,
                    dateKey = today,
                    skinFeel = fs.skinFeel,
                    itchDiscomfort = fs.itchDiscomfort,
                    sleepQuality = fs.sleepQuality,
                    stressLevel = fs.stressLevel,
                    newProductUsed = fs.newProductUsed,
                    newProductNote = fs.newProductNote,
                    notes = fs.notes,
                    createdAt = now,
                )
            )
            _todayCompleted.value = true
            _submitted.value = true
            val all = checkInDao.observeByUserId(userId).first()
            _streak.value = computeStreak(all)
        }
    }

    fun toggleRecording(outputDir: File) {
        if (_recording.value) stopRecording() else startRecording(outputDir)
    }

    private fun startRecording(outputDir: File) {
        _recordingError.value = false
        try {
            val file = File(outputDir, "voicenote_${System.currentTimeMillis()}.mp3")
            mediaRecorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                MediaRecorder(android.app.Application())
            } else {
                @Suppress("DEPRECATION") MediaRecorder()
            }
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            _recording.value = true
        } catch (e: Exception) {
            // Missing RECORD_AUDIO permission or a mic failure — never crash.
            runCatching { mediaRecorder?.release() }
            mediaRecorder = null
            _recording.value = false
            _recordingError.value = true
        }
    }

    private fun stopRecording() {
        mediaRecorder?.apply { stop(); release() }
        mediaRecorder = null
        _recording.value = false
    }

    override fun onCleared() {
        super.onCleared()
        mediaRecorder?.release()
    }

    private fun computeStreak(checkIns: List<DailyCheckInEntity>): Int {
        val dates = checkIns.map { it.dateKey }.sortedDescending().distinct()
        if (dates.isEmpty()) return 0
        val cal = Calendar.getInstance()
        val today = dateFormat.format(cal.time)
        val anchor = dates.first()
        // A streak counts as long as the most recent check-in is today or yesterday.
        if (anchor != today) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
            if (anchor != dateFormat.format(cal.time)) return 0
        }
        // The anchor day itself always counts (fixes yesterday-anchored streaks showing N-1).
        var streak = 1
        cal.time = java.util.Date(dateFormat.parse(anchor)!!.time)
        for (i in 1 until dates.size) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
            if (dateFormat.format(cal.time) == dates[i]) streak++ else break
        }
        return streak
    }
}
