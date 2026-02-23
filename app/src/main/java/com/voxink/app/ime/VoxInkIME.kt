package com.voxink.app.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.View
import android.widget.ImageButton
import com.voxink.app.R
import com.voxink.app.ui.MainActivity
import timber.log.Timber

class VoxInkIME : InputMethodService() {
    private lateinit var actionHandler: KeyboardActionHandler

    override fun onCreateInputView(): View {
        actionHandler =
            KeyboardActionHandler(
                onSendKeyEvent = { keyCode -> sendDownUpKeyEvents(keyCode) },
                onSwitchKeyboard = {
                    switchToPreviousInputMethod()
                },
                onOpenSettings = { launchSettings() },
                onMicTap = {
                    // TODO: Phase 1 — audio recording
                    Timber.d("Mic button tapped")
                },
            )

        val view = layoutInflater.inflate(R.layout.keyboard_view, null)
        bindButtons(view)
        Timber.d("VoxInkIME input view created")
        return view
    }

    private fun bindButtons(view: View) {
        view.findViewById<ImageButton>(R.id.btn_backspace)?.setOnClickListener {
            actionHandler.handle(KeyboardAction.Backspace)
        }
        view.findViewById<ImageButton>(R.id.btn_enter)?.setOnClickListener {
            actionHandler.handle(KeyboardAction.Enter)
        }
        view.findViewById<ImageButton>(R.id.btn_switch)?.setOnClickListener {
            actionHandler.handle(KeyboardAction.SwitchKeyboard)
        }
        view.findViewById<ImageButton>(R.id.btn_mic)?.setOnClickListener {
            actionHandler.handle(KeyboardAction.MicTap)
        }
        view.findViewById<ImageButton>(R.id.btn_settings)?.setOnClickListener {
            actionHandler.handle(KeyboardAction.OpenSettings)
        }
    }

    private fun launchSettings() {
        val intent =
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        startActivity(intent)
    }
}
