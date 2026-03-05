package com.voxpen.app.ui.onboarding

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val isApiKeyConfigured: Boolean = false,
    val isKeyboardEnabled: Boolean = false,
    val hasMicPermission: Boolean = false,
    val hasPracticed: Boolean = false,
    val practiceOriginal: String? = null,
    val practiceRefined: String? = null,
    val isPracticing: Boolean = false,
    val practiceError: String? = null,
)

enum class OnboardingStep {
    WELCOME,
    API_KEY,
    ENABLE_KEYBOARD,
    GRANT_PERMISSION,
    PRACTICE,
    TIPS,
}
