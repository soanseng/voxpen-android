package com.voxink.app.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.voxink.app.R
import com.voxink.app.data.local.PreferencesManager
import com.voxink.app.data.model.RecordingMode
import com.voxink.app.ui.MainActivity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class VoxInkIME : InputMethodService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var actionHandler: KeyboardActionHandler
    private lateinit var recordingController: RecordingController
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var preferencesManager: PreferencesManager

    private var candidateBar: LinearLayout? = null
    private var candidateText: TextView? = null
    private var candidateProgress: ProgressBar? = null
    private var micButton: ImageButton? = null

    override fun onCreateInputView(): View {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            VoxInkIMEEntryPoint::class.java,
        )

        audioRecorder = AudioRecorder(this)
        preferencesManager = entryPoint.preferencesManager()
        recordingController = RecordingController(
            transcribeUseCase = entryPoint.transcribeAudioUseCase(),
            apiKeyManager = entryPoint.apiKeyManager(),
            ioDispatcher = Dispatchers.IO,
        )

        actionHandler = KeyboardActionHandler(
            onSendKeyEvent = { keyCode -> sendDownUpKeyEvents(keyCode) },
            onSwitchKeyboard = {
                switchToPreviousInputMethod()
            },
            onOpenSettings = { launchSettings() },
            onMicTap = { handleMicTap() },
        )

        val view = layoutInflater.inflate(R.layout.keyboard_view, null)
        bindViews(view)
        bindButtons(view)
        observeUiState()
        Timber.d("VoxInkIME input view created")
        return view
    }

    private fun bindViews(view: View) {
        candidateBar = view.findViewById(R.id.candidate_bar)
        candidateText = view.findViewById(R.id.candidate_text)
        candidateProgress = view.findViewById(R.id.candidate_progress)
        micButton = view.findViewById(R.id.btn_mic)
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
        view.findViewById<ImageButton>(R.id.btn_settings)?.setOnClickListener {
            actionHandler.handle(KeyboardAction.OpenSettings)
        }
        setupMicButton(view.findViewById(R.id.btn_mic))
    }

    @Suppress("ClickableViewAccessibility")
    private fun setupMicButton(micBtn: ImageButton?) {
        micBtn ?: return
        serviceScope.launch {
            val mode = preferencesManager.recordingModeFlow.first()
            when (mode) {
                RecordingMode.TAP_TO_TOGGLE -> {
                    micBtn.setOnClickListener { handleMicTap() }
                }
                RecordingMode.HOLD_TO_RECORD -> {
                    micBtn.setOnTouchListener { _, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> { startRecording(); true }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                stopRecording(); true
                            }
                            else -> false
                        }
                    }
                }
            }
        }
    }

    private fun handleMicTap() {
        when (recordingController.uiState.value) {
            ImeUiState.Idle, is ImeUiState.Error, is ImeUiState.Result -> startRecording()
            ImeUiState.Recording -> stopRecording()
            ImeUiState.Processing -> { /* ignore */ }
        }
    }

    private fun startRecording() {
        if (!audioRecorder.hasPermission()) {
            candidateBar?.visibility = View.VISIBLE
            candidateText?.text = getString(R.string.mic_permission_required)
            candidateProgress?.visibility = View.GONE
            return
        }
        recordingController.onStartRecording { audioRecorder.startRecording() }
    }

    private fun stopRecording() {
        serviceScope.launch {
            val language = preferencesManager.languageFlow.first()
            recordingController.onStopRecording(
                stopRecording = { audioRecorder.stopRecording() },
                language = language,
            )
        }
    }

    private fun observeUiState() {
        serviceScope.launch {
            recordingController.uiState.collect { state -> updateUi(state) }
        }
    }

    private fun updateUi(state: ImeUiState) {
        when (state) {
            ImeUiState.Idle -> {
                candidateBar?.visibility = View.GONE
                candidateBar?.setOnClickListener(null)
            }
            ImeUiState.Recording -> {
                candidateBar?.visibility = View.VISIBLE
                candidateProgress?.visibility = View.GONE
                candidateText?.text = getString(R.string.recording)
            }
            ImeUiState.Processing -> {
                candidateBar?.visibility = View.VISIBLE
                candidateProgress?.visibility = View.VISIBLE
                candidateText?.text = getString(R.string.processing)
            }
            is ImeUiState.Result -> {
                candidateBar?.visibility = View.VISIBLE
                candidateProgress?.visibility = View.GONE
                candidateText?.text = state.text
                candidateBar?.setOnClickListener {
                    currentInputConnection?.commitText(state.text, 1)
                    recordingController.dismiss()
                }
            }
            is ImeUiState.Error -> {
                candidateBar?.visibility = View.VISIBLE
                candidateProgress?.visibility = View.GONE
                candidateText?.text = state.message
                candidateBar?.setOnClickListener { recordingController.dismiss() }
            }
        }
        micButton?.setBackgroundColor(
            getColor(if (state == ImeUiState.Recording) R.color.mic_active else R.color.mic_idle),
        )
    }

    private fun launchSettings() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override fun onDestroy() {
        audioRecorder.release()
        recordingController.destroy()
        serviceScope.cancel()
        super.onDestroy()
    }
}
