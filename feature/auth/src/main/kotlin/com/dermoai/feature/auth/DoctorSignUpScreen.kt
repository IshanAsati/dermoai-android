package com.dermoai.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dermoai.core.common.ui.UiState
import com.dermoai.core.ui.components.DermoGlassCard
import com.dermoai.core.ui.components.GradientHeader
import com.dermoai.core.ui.components.MedicalDisclaimerBar
import com.dermoai.core.ui.components.NeuButton
import com.dermoai.core.ui.components.NeuSurface
import com.dermoai.core.ui.components.NeuSurfaceStyle
import com.dermoai.core.ui.theme.DermoColors

/**
 * Registration for a clinician account.
 *
 * A longer form than [SignUpScreen] because it is doing a different job: the
 * patient form only needs enough to create a login, while this one is gathering
 * a claim that a human will later check against a public register. Everything
 * asked for here exists because a reviewer needs it.
 *
 * The copy is blunt about what submitting does and does not do. A form that
 * looks like it grants access and then silently doesn't is worse than one that
 * says up front that nothing unlocks until a person approves it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DoctorSignUpScreen(
    onSignedUp: () -> Unit,
    onNavigateToSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DoctorSignUpViewModel = hiltViewModel(),
) {
    val form by viewModel.formState.collectAsStateWithLifecycle()
    val result by viewModel.signUpResult.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(result) {
        if (result is UiState.Success) {
            viewModel.clearResult()
            onSignedUp()
        }
    }

    val isLoading = result is UiState.Loading

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        GradientHeader(
            title = stringResource(R.string.doctor_sign_up_title),
            subtitle = stringResource(R.string.doctor_sign_up_subtitle),
        )
        MedicalDisclaimerBar()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (viewModel.isLocalAuthMode) {
                DermoGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.auth_local_mode_banner),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // ── Account ─────────────────────────────────────────────────────
            SectionHeader(stringResource(R.string.doctor_sign_up_section_account))

            DoctorTextField(
                value = form.fullName,
                onValueChange = viewModel::onFullNameChange,
                label = stringResource(R.string.doctor_field_full_name),
                error = form.visibleError(DoctorSignUpField.FULL_NAME),
                enabled = !isLoading,
            )
            DoctorTextField(
                value = form.email,
                onValueChange = viewModel::onEmailChange,
                label = stringResource(R.string.doctor_field_email),
                error = form.visibleError(DoctorSignUpField.EMAIL),
                enabled = !isLoading,
                keyboardType = KeyboardType.Email,
            )
            DoctorTextField(
                value = form.password,
                onValueChange = viewModel::onPasswordChange,
                label = stringResource(R.string.doctor_field_password),
                error = form.visibleError(DoctorSignUpField.PASSWORD),
                enabled = !isLoading,
                keyboardType = KeyboardType.Password,
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Outlined.VisibilityOff
                            } else {
                                Icons.Outlined.Visibility
                            },
                            contentDescription = stringResource(
                                if (passwordVisible) {
                                    R.string.doctor_hide_password
                                } else {
                                    R.string.doctor_show_password
                                },
                            ),
                        )
                    }
                },
            )

            // ── Credentials ─────────────────────────────────────────────────
            SectionHeader(stringResource(R.string.doctor_sign_up_section_credentials))

            DoctorTextField(
                value = form.registrationNumber,
                onValueChange = viewModel::onRegistrationNumberChange,
                label = stringResource(R.string.doctor_field_registration_number),
                error = form.visibleError(DoctorSignUpField.REGISTRATION_NUMBER),
                enabled = !isLoading,
            )
            DoctorTextField(
                value = form.specialty,
                onValueChange = viewModel::onSpecialtyChange,
                label = stringResource(R.string.doctor_field_specialty),
                error = null,
                enabled = !isLoading,
                supportingText = stringResource(R.string.doctor_sign_up_optional),
            )
            DoctorTextField(
                value = form.institution,
                onValueChange = viewModel::onInstitutionChange,
                label = stringResource(R.string.doctor_field_institution),
                error = null,
                enabled = !isLoading,
                supportingText = stringResource(R.string.doctor_sign_up_optional),
            )
            DoctorTextField(
                value = form.yearsExperience,
                onValueChange = viewModel::onYearsExperienceChange,
                label = stringResource(R.string.doctor_field_years_experience),
                error = form.visibleError(DoctorSignUpField.YEARS_EXPERIENCE),
                enabled = !isLoading,
                keyboardType = KeyboardType.Number,
            )

            // Qualifications are a list because a clinician routinely holds
            // several and a reviewer reads them as separate claims; a single
            // comma-separated field would make "MD, Dermatology" ambiguous.
            SectionHeader(stringResource(R.string.doctor_field_qualifications))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                OutlinedTextField(
                    value = form.qualificationDraft,
                    onValueChange = viewModel::onQualificationDraftChange,
                    label = { Text(stringResource(R.string.doctor_field_qualification)) },
                    placeholder = { Text(stringResource(R.string.doctor_field_qualification_hint)) },
                    singleLine = true,
                    enabled = !isLoading,
                    isError = form.visibleError(DoctorSignUpField.QUALIFICATIONS) != null,
                    supportingText = form.visibleError(DoctorSignUpField.QUALIFICATIONS)?.let {
                        { Text(it.message(), color = MaterialTheme.colorScheme.error) }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { viewModel.addQualification() }),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
                NeuButton(
                    onClick = viewModel::addQualification,
                    enabled = !isLoading && form.qualificationDraft.isNotBlank(),
                    // 56.dp matches the text field's height and clears the 48.dp
                    // minimum touch target.
                    modifier = Modifier.height(56.dp),
                    containerColor = DermoColors.Teal,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.doctor_qualification_add))
                }
            }
            if (form.qualifications.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    form.qualifications.forEach { qualification ->
                        // Tapping the chip removes it, so the whole chip is
                        // described as the action rather than leaving TalkBack to
                        // announce a bare qualification with a mystery close icon.
                        val removeLabel =
                            stringResource(R.string.doctor_qualification_remove, qualification)
                        InputChip(
                            selected = false,
                            onClick = { viewModel.removeQualification(qualification) },
                            label = { Text(qualification) },
                            enabled = !isLoading,
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            modifier = Modifier.semantics { contentDescription = removeLabel },
                        )
                    }
                }
            }

            // ── About ───────────────────────────────────────────────────────
            SectionHeader(stringResource(R.string.doctor_sign_up_section_about))
            OutlinedTextField(
                value = form.bio,
                onValueChange = viewModel::onBioChange,
                label = { Text(stringResource(R.string.doctor_field_bio)) },
                placeholder = { Text(stringResource(R.string.doctor_field_bio_hint)) },
                enabled = !isLoading,
                minLines = 3,
                supportingText = { Text(stringResource(R.string.doctor_sign_up_optional)) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            // Sits directly above the button so it cannot be scrolled past on
            // the way to submitting.
            NeuSurface(
                modifier = Modifier.fillMaxWidth(),
                style = NeuSurfaceStyle.Inset,
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.doctor_sign_up_review_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = DermoColors.AmberText,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }

            if (form.submitError != null) {
                Text(
                    text = form.submitError.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            NeuButton(
                onClick = {
                    keyboard?.hide()
                    viewModel.submit()
                },
                enabled = !isLoading && form.isValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                containerColor = DermoColors.Teal,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.doctor_sign_up_submit))
                }
            }

            TextButton(
                onClick = onNavigateToSignIn,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.doctor_sign_up_have_account))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/** Section label. Uses TealText, not Teal — this size fails AA on the accent. */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = DermoColors.TealText,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * One validated field. Wrapped rather than repeated nine times so the error
 * colour, shape and supporting-text behaviour cannot drift between fields.
 */
@Composable
private fun DoctorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: DoctorSignUpError?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    supportingText: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val helper = error?.message() ?: supportingText
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        supportingText = helper?.let {
            {
                Text(
                    text = it,
                    color = if (error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        DermoColors.Slate
                    },
                )
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Next,
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Localised text for a validation reason.
 *
 * The mapping lives in the UI layer because the ViewModel has no resources and
 * the app ships in four languages — an English string built in the ViewModel
 * would never be translated.
 */
@Composable
private fun DoctorSignUpError.message(): String = stringResource(
    when (this) {
        DoctorSignUpError.REQUIRED -> R.string.doctor_error_required
        DoctorSignUpError.INVALID_EMAIL -> R.string.doctor_error_invalid_email
        DoctorSignUpError.PASSWORD_TOO_SHORT -> R.string.doctor_error_password_too_short
        DoctorSignUpError.NO_QUALIFICATIONS -> R.string.doctor_error_no_qualifications
        DoctorSignUpError.YEARS_NOT_A_NUMBER -> R.string.doctor_error_years_not_a_number
        DoctorSignUpError.YEARS_OUT_OF_RANGE -> R.string.doctor_error_years_out_of_range
    },
)
