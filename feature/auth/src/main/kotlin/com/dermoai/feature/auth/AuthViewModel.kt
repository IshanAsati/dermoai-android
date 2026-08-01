package com.dermoai.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dermoai.core.common.result.AppResult
import com.dermoai.core.common.ui.UiState
import com.dermoai.core.domain.model.AuthUser
import com.dermoai.core.domain.repository.AuthRepository
import com.dermoai.core.domain.usecase.auth.SignInWithEmailUseCase
import com.dermoai.core.domain.usecase.auth.SignInWithGoogleUseCase
import com.dermoai.core.domain.usecase.auth.SignUpWithEmailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for email/password and Google sign-in / sign-up screens.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val signInWithEmail: SignInWithEmailUseCase,
    private val signUpWithEmail: SignUpWithEmailUseCase,
    private val signInWithGoogle: SignInWithGoogleUseCase,
    authRepository: AuthRepository,
) : ViewModel() {

    private val _formState = MutableStateFlow(AuthFormState())
    val formState: StateFlow<AuthFormState> = _formState.asStateFlow()

    private val _authResult = MutableStateFlow<UiState<AuthUser>>(UiState.Idle)
    val authResult: StateFlow<UiState<AuthUser>> = _authResult.asStateFlow()

    val isLocalAuthMode: Boolean = authRepository.isLocalAuthMode()

    fun onEmailChange(value: String) {
        _formState.update { it.copy(email = value, errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _formState.update { it.copy(password = value, errorMessage = null) }
    }

    fun onDisplayNameChange(value: String) {
        _formState.update { it.copy(displayName = value, errorMessage = null) }
    }

    fun signIn() {
        val form = _formState.value
        viewModelScope.launch {
            _authResult.value = UiState.Loading
            when (val result = signInWithEmail(form.email, form.password)) {
                is AppResult.Success -> _authResult.value = UiState.Success(result.data)
                is AppResult.Error -> {
                    _authResult.value = UiState.Error(result.message ?: "Sign-in failed")
                    _formState.update { it.copy(errorMessage = result.message) }
                }
                is AppResult.Loading -> Unit
            }
        }
    }

    fun signUp() {
        val form = _formState.value
        viewModelScope.launch {
            _authResult.value = UiState.Loading
            when (
                val result = signUpWithEmail(
                    email = form.email,
                    password = form.password,
                    displayName = form.displayName,
                )
            ) {
                is AppResult.Success -> _authResult.value = UiState.Success(result.data)
                is AppResult.Error -> {
                    _authResult.value = UiState.Error(result.message ?: "Sign-up failed")
                    _formState.update { it.copy(errorMessage = result.message) }
                }
                is AppResult.Loading -> Unit
            }
        }
    }

    fun signInWithGoogleToken(idToken: String) {
        viewModelScope.launch {
            _authResult.value = UiState.Loading
            when (val result = signInWithGoogle(idToken)) {
                is AppResult.Success -> _authResult.value = UiState.Success(result.data)
                is AppResult.Error -> {
                    _authResult.value = UiState.Error(result.message ?: "Google Sign-In failed")
                    _formState.update { it.copy(errorMessage = result.message) }
                }
                is AppResult.Loading -> Unit
            }
        }
    }

    fun clearResult() {
        _authResult.value = UiState.Idle
    }
}

/**
 * Controlled form fields for auth screens.
 */
data class AuthFormState(
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val errorMessage: String? = null,
)
