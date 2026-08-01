package com.dermoai.feature.treatment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dermoai.core.database.dao.RoutineStepDao
import com.dermoai.core.database.dao.StepCompletionDao
import com.dermoai.core.database.dao.TreatmentRoutineDao
import com.dermoai.core.database.entity.RoutineStepEntity
import com.dermoai.core.database.entity.StepCompletionEntity
import com.dermoai.core.database.entity.TreatmentRoutineEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TreatmentViewModel @Inject constructor(
    private val routineDao: TreatmentRoutineDao,
    private val stepDao: RoutineStepDao,
    private val completionDao: StepCompletionDao,
) : ViewModel() {

    private val _routines = MutableStateFlow<List<TreatmentRoutineEntity>>(emptyList())
    val routines: StateFlow<List<TreatmentRoutineEntity>> = _routines.asStateFlow()

    private val _steps = MutableStateFlow<List<RoutineStepEntity>>(emptyList())
    val steps: StateFlow<List<RoutineStepEntity>> = _steps.asStateFlow()

    private val _completedStepIds = MutableStateFlow<Set<String>>(emptySet())
    val completedStepIds: StateFlow<Set<String>> = _completedStepIds.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedRoutineId = MutableStateFlow<String?>(null)
    val selectedRoutineId: StateFlow<String?> = _selectedRoutineId.asStateFlow()

    val newRoutineName = MutableStateFlow("")

    private val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    fun loadRoutines(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            routineDao.observeByUserId(userId).collect {
                _routines.value = it
                _isLoading.value = false
            }
        }
    }

    fun selectRoutine(routineId: String?) {
        _selectedRoutineId.value = routineId
        if (routineId != null) {
            viewModelScope.launch {
                stepDao.observeByRoutineId(routineId).collect { _steps.value = it }
                completionDao.observeByRoutineAndDate(routineId, todayKey).collect { completions ->
                    _completedStepIds.value = completions.map { it.stepId }.toSet()
                }
            }
        }
    }

    fun createRoutine(userId: String) {
        val name = newRoutineName.value.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            val id = "routine_${UUID.randomUUID()}"
            routineDao.upsert(TreatmentRoutineEntity(id, userId, name, null, System.currentTimeMillis(), System.currentTimeMillis()))
            stepDao.upsert(RoutineStepEntity("step_${UUID.randomUUID()}", id, "Apply product", "Morning", 0))
            stepDao.upsert(RoutineStepEntity("step_${UUID.randomUUID()}", id, "Apply product", "Evening", 1))
            newRoutineName.value = ""
            selectRoutine(id)
        }
    }

    fun renameRoutine(routineId: String, newName: String) {
        viewModelScope.launch {
            val r = routineDao.getById(routineId) ?: return@launch
            routineDao.upsert(r.copy(name = newName, updatedAt = System.currentTimeMillis()))
        }
    }

    fun addStep(routineId: String, productName: String, timeOfDay: String) {
        viewModelScope.launch {
            val currentSteps = stepDao.observeByRoutineId(routineId).first()
            val maxOrder = currentSteps.maxOfOrNull { it.sortOrder } ?: -1
            stepDao.upsert(RoutineStepEntity("step_${UUID.randomUUID()}", routineId, productName, timeOfDay, maxOrder + 1))
        }
    }

    fun updateStep(stepId: String, productName: String, timeOfDay: String) {
        viewModelScope.launch {
            val s = _steps.value.find { it.id == stepId } ?: return@launch
            stepDao.upsert(s.copy(productName = productName, timeOfDay = timeOfDay))
        }
    }

    fun reorderSteps(routineId: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val mutable = _steps.value.toMutableList()
            if (fromIndex !in mutable.indices || toIndex !in mutable.indices) return@launch
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            mutable.forEachIndexed { i, step -> stepDao.upsert(step.copy(sortOrder = i)) }
        }
    }

    fun deleteStep(stepId: String) {
        viewModelScope.launch {
            val routineId = _selectedRoutineId.value ?: return@launch
            stepDao.deleteById(stepId)
            val remaining = _steps.value.filter { it.id != stepId }
            remaining.forEachIndexed { i, step ->
                stepDao.upsert(step.copy(sortOrder = i))
            }
        }
    }

    fun toggleStep(stepId: String) {
        viewModelScope.launch {
            val existing = completionDao.getByStepAndDate(stepId, todayKey)
            if (existing != null) {
                completionDao.deleteByStepAndDate(stepId, todayKey)
            } else {
                val routineId = _selectedRoutineId.value ?: return@launch
                completionDao.upsert(StepCompletionEntity("comp_${UUID.randomUUID()}", stepId, routineId, System.currentTimeMillis(), todayKey))
            }
        }
    }

    fun deleteRoutine(routineId: String) {
        viewModelScope.launch {
            stepDao.deleteByRoutineId(routineId)
            routineDao.deleteById(routineId)
            _selectedRoutineId.value = null
        }
    }
}
