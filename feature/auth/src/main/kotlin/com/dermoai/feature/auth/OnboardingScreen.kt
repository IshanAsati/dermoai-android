package com.dermoai.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dermoai.core.ui.components.NeuButton
import com.dermoai.core.ui.components.NeuSurface
import com.dermoai.core.ui.components.OutlinedNeuButton
import com.dermoai.core.ui.theme.DermoColors
import kotlinx.coroutines.launch

private val LANGUAGES = listOf(
    "en" to "English",
    "hi" to "हिन्दी (Hindi)",
    "bn" to "বাংলা (Bengali)",
    "mr" to "मराठी (Marathi)",
    "ta" to "தமிழ் (Tamil)",
    "te" to "తెలుగు (Telugu)",
)
private val SKIN_TYPES = listOf("Normal", "Oily", "Dry", "Combination", "Sensitive")
private val SKIN_TONES = listOf("Very Fair", "Fair", "Medium", "Olive", "Brown", "Dark Brown")
private val GENDERS = listOf("Male", "Female", "Non-binary", "Prefer not to say")
private val SUN_EXPOSURES = listOf("Indoors mostly", "15-30 min daily", "1-2 hours daily", "Outdoor worker")
private val STRESS_LEVELS = listOf("Low", "Moderate", "High", "Very High")
private val DIET = listOf("Vegetarian", "Vegan", "Non-vegetarian", "Mixed")
private val EXERCISE = listOf("Daily", "3-5x/week", "1-2x/week", "Rarely")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf(0) }
    var language by rememberSaveable { mutableStateOf("en") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("") }
    var skinType by rememberSaveable { mutableStateOf("") }
    var skinTone by rememberSaveable { mutableStateOf("") }
    var skinConcerns by rememberSaveable { mutableStateOf("") }
    var allergies by rememberSaveable { mutableStateOf("") }
    var medications by rememberSaveable { mutableStateOf("") }
    var sunExposure by rememberSaveable { mutableStateOf("") }
    var waterIntake by rememberSaveable { mutableStateOf("") }
    var sleepHours by rememberSaveable { mutableStateOf("") }
    var stressLevel by rememberSaveable { mutableStateOf("") }
    var diet by rememberSaveable { mutableStateOf("") }
    var smoking by rememberSaveable { mutableStateOf(false) }
    var alcohol by rememberSaveable { mutableStateOf(false) }
    var exercise by rememberSaveable { mutableStateOf("") }
    var skinCareRoutine by rememberSaveable { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { viewModel.complete() }) { Text("Skip all") }
            Text("Step ${page + 1} / 4", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(4) { i -> Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= page) DermoColors.Teal else DermoColors.Line.copy(alpha = 0.5f))) }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (page) {
                0 -> LanguagePage(language, { language = it }, displayName, { displayName = it }, age, { age = it }, gender, { gender = it })
                1 -> SkinProfilePage(skinType, { skinType = it }, skinTone, { skinTone = it }, skinConcerns, { skinConcerns = it }, allergies, { allergies = it }, medications, { medications = it }, skinCareRoutine, { skinCareRoutine = it })
                2 -> LifestylePage(sunExposure, { sunExposure = it }, waterIntake, { waterIntake = it }, sleepHours, { sleepHours = it }, stressLevel, { stressLevel = it }, diet, { diet = it }, smoking, { smoking = it }, alcohol, { alcohol = it }, exercise, { exercise = it })
                3 -> SummaryPage(displayName, age, gender, skinType, skinTone, language)
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (page > 0) {
                OutlinedNeuButton(onClick = { page-- }, modifier = Modifier.weight(1f)) { Text("Back") }
            }
            NeuButton(
                onClick = {
                    scope.launch {
                        if (page < 3) page++ else viewModel.complete(
                            OnboardingProfile(
                                displayName = displayName,
                                age = age,
                                gender = gender,
                                skinType = skinType,
                                skinTone = skinTone,
                                skinConcerns = skinConcerns,
                                allergies = allergies,
                                medications = medications,
                                sunExposure = sunExposure,
                                waterIntake = waterIntake,
                                sleepHours = sleepHours,
                                stressLevel = stressLevel,
                                diet = diet,
                                smoking = smoking,
                                alcohol = alcohol,
                                exercise = exercise,
                                skinCareRoutine = skinCareRoutine,
                                language = language,
                            )
                        )
                    }
                },
                modifier = Modifier.weight(1f),
                containerColor = DermoColors.Teal,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Text(if (page < 3) "Next" else "Done") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePage(language: String, onLanguageChange: (String) -> Unit, displayName: String, onDisplayNameChange: (String) -> Unit, age: String, onAgeChange: (String) -> Unit, gender: String, onGenderChange: (String) -> Unit) {
    var langExp by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Welcome to DermoAI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Let's personalize your experience", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("App Language", style = MaterialTheme.typography.labelLarge, color = DermoColors.TealText, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        ExposedDropdownMenuBox(expanded = langExp, onExpandedChange = { langExp = it }) {
            OutlinedTextField(value = LANGUAGES.find { it.first == language }?.second ?: "English", onValueChange = {}, readOnly = true, label = { Text("Language") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(langExp) }, modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(14.dp))
            ExposedDropdownMenu(expanded = langExp, onDismissRequest = { langExp = false }) { LANGUAGES.forEach { (code, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { onLanguageChange(code); langExp = false }) } }
        }
        Text("About You", style = MaterialTheme.typography.labelLarge, color = DermoColors.TealText, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        OutlinedTextField(value = displayName, onValueChange = onDisplayNameChange, label = { Text("Your name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = age, onValueChange = onAgeChange, label = { Text("Age") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), singleLine = true)
        Spacer(Modifier.height(12.dp))
        DropdownField("Gender", gender, GENDERS) { onGenderChange(it) }
        Spacer(Modifier.height(40.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkinProfilePage(skinType: String, onSkinTypeChange: (String) -> Unit, skinTone: String, onSkinToneChange: (String) -> Unit, skinConcerns: String, onSkinConcernsChange: (String) -> Unit, allergies: String, onAllergiesChange: (String) -> Unit, medications: String, onMedicationsChange: (String) -> Unit, skinCareRoutine: String, onSkinCareRoutineChange: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Skin Profile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Help us provide relevant information", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionH("Skin Type"); DropdownField("Skin type", skinType, SKIN_TYPES) { onSkinTypeChange(it) }
        SectionH("Skin Tone"); DropdownField("Skin tone", skinTone, SKIN_TONES) { onSkinToneChange(it) }
        SectionH("Primary skin concerns"); OutlinedTextField(value = skinConcerns, onValueChange = onSkinConcernsChange, label = { Text("e.g. Acne, dryness, pigmentation") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), singleLine = true)
        SectionH("Allergies"); OutlinedTextField(value = allergies, onValueChange = onAllergiesChange, label = { Text("e.g. Fragrances, SPF, nickel") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), singleLine = true)
        SectionH("Current medications"); OutlinedTextField(value = medications, onValueChange = onMedicationsChange, label = { Text("Any topical or oral medications") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), singleLine = true)
        SectionH("Current skin care routine"); OutlinedTextField(value = skinCareRoutine, onValueChange = onSkinCareRoutineChange, label = { Text("Describe your routine") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), singleLine = true)
        Spacer(Modifier.height(40.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LifestylePage(sunExposure: String, onSunExposureChange: (String) -> Unit, waterIntake: String, onWaterIntakeChange: (String) -> Unit, sleepHours: String, onSleepHoursChange: (String) -> Unit, stressLevel: String, onStressLevelChange: (String) -> Unit, diet: String, onDietChange: (String) -> Unit, smoking: Boolean, onSmokingChange: (Boolean) -> Unit, alcohol: Boolean, onAlcoholChange: (Boolean) -> Unit, exercise: String, onExerciseChange: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Lifestyle & Habits", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("These factors affect skin health", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionH("Sun Exposure"); DropdownField("Daily sun exposure", sunExposure, SUN_EXPOSURES) { onSunExposureChange(it) }
        SectionH("Water Intake"); OutlinedTextField(value = waterIntake, onValueChange = onWaterIntakeChange, label = { Text("Glasses per day") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), singleLine = true)
        SectionH("Sleep Hours"); OutlinedTextField(value = sleepHours, onValueChange = onSleepHoursChange, label = { Text("Hours per night") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), singleLine = true)
        SectionH("Stress Level"); DropdownField("Stress level", stressLevel, STRESS_LEVELS) { onStressLevelChange(it) }
        SectionH("Diet"); DropdownField("Diet type", diet, DIET) { onDietChange(it) }
        SectionH("Exercise"); DropdownField("Exercise frequency", exercise, EXERCISE) { onExerciseChange(it) }
        SectionH("Lifestyle")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column { Text("Smoking", style = MaterialTheme.typography.bodyMedium); Switch(checked = smoking, onCheckedChange = onSmokingChange) }
            Column { Text("Alcohol", style = MaterialTheme.typography.bodyMedium); Switch(checked = alcohol, onCheckedChange = onAlcoholChange) }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SummaryPage(displayName: String, age: String, gender: String, skinType: String, skinTone: String, language: String) {
    val langLabel = LANGUAGES.find { it.first == language }?.second ?: "English"
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Summary", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Review your details", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        NeuSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Name", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(displayName.ifEmpty { "—" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Age", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(age.ifEmpty { "—" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Gender", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(gender.ifEmpty { "—" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Skin Type", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(skinType.ifEmpty { "—" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Skin Tone", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(skinTone.ifEmpty { "—" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Language", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(langLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(value = value, onValueChange = {}, readOnly = true, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(14.dp))
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { onSelect(o); expanded = false }) } }
    }
}

@Composable
private fun SectionH(text: String) { Text(text, style = MaterialTheme.typography.labelLarge, color = DermoColors.TealText, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) }
