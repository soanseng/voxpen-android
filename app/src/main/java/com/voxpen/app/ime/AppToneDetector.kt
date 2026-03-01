package com.voxpen.app.ime

import com.voxpen.app.data.model.BuiltinAppToneTable
import com.voxpen.app.data.model.ToneStyle

object AppToneDetector {
    // Avoid android.text.InputType dependency so this is testable in JVM unit tests.
    // android.text.InputType constants:
    //   TYPE_TEXT_VARIATION_MASK          = 0x00000ff0
    //   TYPE_TEXT_VARIATION_SHORT_MESSAGE = 0x00000010
    private const val TYPE_TEXT_VARIATION_MASK = 0x00000ff0
    private const val TYPE_TEXT_VARIATION_SHORT_MESSAGE = 0x00000010

    /**
     * Detects the appropriate tone for the given context.
     * Priority: custom rules → builtin table → SHORT_MESSAGE inputType → null
     *
     * @param packageName The package name of the app receiving input.
     * @param inputType   The inputType bitmask from the current EditorInfo.
     * @param customRules User-defined package → ToneStyle overrides (highest priority).
     * @return The resolved [ToneStyle], or null if no match is found.
     */
    fun detect(
        packageName: String,
        inputType: Int,
        customRules: Map<String, ToneStyle>,
    ): ToneStyle? {
        if (packageName.isBlank()) return null

        // 1. User custom rules (highest priority)
        customRules[packageName]?.let { return it }

        // 2. Builtin package name table
        BuiltinAppToneTable.rules[packageName]?.let { return it }

        // 3. SHORT_MESSAGE inputType fallback
        val variation = inputType and TYPE_TEXT_VARIATION_MASK
        if (variation == TYPE_TEXT_VARIATION_SHORT_MESSAGE) {
            return ToneStyle.Casual
        }

        return null
    }
}
