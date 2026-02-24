package com.voxink.app.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import com.voxink.app.R
import com.voxink.app.data.local.PreferencesManager
import com.voxink.app.data.model.RecordingMode
import com.voxink.app.data.model.SttLanguage
import com.voxink.app.ui.MainActivity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

@Suppress("TooManyFunctions")
class VoxInkIME : InputMethodService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var actionHandler: KeyboardActionHandler
    private lateinit var recordingController: RecordingController
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var billingManager: com.voxink.app.billing.BillingManager

    private var candidateBar: LinearLayout? = null
    private var candidateStatusRow: LinearLayout? = null
    private var candidateText: TextView? = null
    private var candidateProgress: ProgressBar? = null
    private var candidateOriginal: TextView? = null
    private var candidateRefinedRow: LinearLayout? = null
    private var candidateRefined: TextView? = null
    private var refineProgress: ProgressBar? = null
    private var micButton: ImageButton? = null

    // Task 6: Mic pulse animation
    private var micPulseAnimator: android.animation.AnimatorSet? = null

    // Task 7: Previous state tracking for haptic/sound feedback
    private var previousUiState: ImeUiState = ImeUiState.Idle

    // Task 8: Recording timer
    private var recordingStartTime: Long = 0
    private val timerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val timerRunnable =
        object : Runnable {
            override fun run() {
                val elapsed = (System.currentTimeMillis() - recordingStartTime) / 1000
                val minutes = elapsed / 60
                val seconds = elapsed % 60
                candidateText?.text = getString(R.string.recording) + " $minutes:%02d".format(seconds)
                timerHandler.postDelayed(this, 1000)
            }
        }

    override fun onCreateInputView(): View {
        val entryPoint =
            EntryPointAccessors.fromApplication(
                applicationContext,
                VoxInkIMEEntryPoint::class.java,
            )

        audioRecorder = AudioRecorder(this)
        preferencesManager = entryPoint.preferencesManager()
        billingManager = entryPoint.billingManager()
        recordingController =
            RecordingController(
                transcribeUseCase = entryPoint.transcribeAudioUseCase(),
                refineTextUseCase = entryPoint.refineTextUseCase(),
                apiKeyManager = entryPoint.apiKeyManager(),
                preferencesManager = preferencesManager,
                dictionaryRepository = entryPoint.dictionaryRepository(),
                usageLimiter = entryPoint.usageLimiter(),
                proStatusProvider = { billingManager.proStatus.value },
                ioDispatcher = Dispatchers.IO,
            )

        actionHandler =
            KeyboardActionHandler(
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
        serviceScope.launch {
            val shown = preferencesManager.keyboardTooltipsShownFlow.first()
            if (!shown) {
                showKeyboardTooltips(view)
                preferencesManager.setKeyboardTooltipsShown(true)
            }
        }
        Timber.d("VoxInkIME input view created")
        return view
    }

    private fun bindViews(view: View) {
        candidateBar = view.findViewById(R.id.candidate_bar)
        candidateStatusRow = view.findViewById(R.id.candidate_status_row)
        candidateText = view.findViewById(R.id.candidate_text)
        candidateProgress = view.findViewById(R.id.candidate_progress)
        candidateOriginal = view.findViewById(R.id.candidate_original)
        candidateRefinedRow = view.findViewById(R.id.candidate_refined_row)
        candidateRefined = view.findViewById(R.id.candidate_refined)
        refineProgress = view.findViewById(R.id.refine_progress)
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
        view.findViewById<ImageButton>(R.id.btn_settings)?.let { settingsBtn ->
            settingsBtn.setOnClickListener { actionHandler.handle(KeyboardAction.OpenSettings) }
            settingsBtn.setOnLongClickListener {
                showQuickSettings(it)
                true
            }
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
                            MotionEvent.ACTION_DOWN -> {
                                startRecording()
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                stopRecording()
                                true
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
            ImeUiState.Idle, is ImeUiState.Error, is ImeUiState.Result,
            is ImeUiState.Refined,
            -> startRecording()
            ImeUiState.Recording -> stopRecording()
            ImeUiState.Processing, is ImeUiState.Refining -> { /* ignore */ }
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

    // Task 6: Start mic pulse animation
    private fun startMicPulse(micBtn: ImageButton) {
        val scaleX = android.animation.ObjectAnimator.ofFloat(micBtn, "scaleX", 1f, 1.15f, 1f)
        val scaleY = android.animation.ObjectAnimator.ofFloat(micBtn, "scaleY", 1f, 1.15f, 1f)
        val alpha = android.animation.ObjectAnimator.ofFloat(micBtn, "alpha", 1f, 0.7f, 1f)
        micPulseAnimator =
            android.animation.AnimatorSet().apply {
                playTogether(scaleX, scaleY, alpha)
                duration = 800
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                addListener(
                    object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            if (recordingController.uiState.value == ImeUiState.Recording) {
                                start()
                            }
                        }
                    },
                )
                start()
            }
    }

    // Task 6: Stop mic pulse animation
    private fun stopMicPulse() {
        micPulseAnimator?.cancel()
        micPulseAnimator = null
        micButton?.apply {
            scaleX = 1f
            scaleY = 1f
            this.alpha = 1f
        }
    }

    // Task 7: Haptic feedback
    private fun performHaptic(type: Int) {
        micButton?.performHapticFeedback(type)
    }

    // Task 7: Sound effects
    private fun playTone(toneType: Int) {
        try {
            val toneGen =
                android.media.ToneGenerator(
                    android.media.AudioManager.STREAM_NOTIFICATION,
                    30,
                )
            toneGen.startTone(toneType, 100)
            android.os.Handler(mainLooper).postDelayed({ toneGen.release() }, 200)
        } catch (_: Exception) {
        }
    }

    private fun updateUi(state: ImeUiState) {
        if (state == previousUiState) return
        previousUiState = state

        triggerStateFeedback(state)
        resetClickListeners()
        updateCandidateBar(state)
        updateMicAppearance(state)
    }

    private fun triggerStateFeedback(state: ImeUiState) {
        when (state) {
            ImeUiState.Recording -> {
                performHaptic(HapticFeedbackConstants.LONG_PRESS)
                playTone(android.media.ToneGenerator.TONE_PROP_BEEP)
            }
            ImeUiState.Processing -> {
                performHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                playTone(android.media.ToneGenerator.TONE_PROP_ACK)
            }
            is ImeUiState.Result, is ImeUiState.Refined -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                } else {
                    performHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                }
            }
            else -> { /* no feedback */ }
        }
    }

    private fun resetClickListeners() {
        candidateBar?.setOnClickListener(null)
        candidateOriginal?.setOnClickListener(null)
        candidateRefinedRow?.setOnClickListener(null)
    }

    private fun updateCandidateBar(state: ImeUiState) {
        when (state) {
            ImeUiState.Idle -> {
                timerHandler.removeCallbacks(timerRunnable)
                candidateBar?.visibility = View.GONE
            }
            ImeUiState.Recording -> {
                showStatusRow(getString(R.string.recording), showProgress = false)
                recordingStartTime = System.currentTimeMillis()
                timerHandler.post(timerRunnable)
            }
            ImeUiState.Processing -> {
                timerHandler.removeCallbacks(timerRunnable)
                showStatusRow(getString(R.string.processing), showProgress = true)
            }
            is ImeUiState.Result -> {
                timerHandler.removeCallbacks(timerRunnable)
                showStatusRow(state.text, showProgress = false)
                candidateBar?.setOnClickListener {
                    currentInputConnection?.commitText(state.text, 1)
                    recordingController.dismiss()
                }
            }
            is ImeUiState.Refining -> {
                timerHandler.removeCallbacks(timerRunnable)
                showDualRows(state.original, null)
            }
            is ImeUiState.Refined -> {
                timerHandler.removeCallbacks(timerRunnable)
                showDualRows(state.original, state.refined)
                candidateOriginal?.setOnClickListener {
                    currentInputConnection?.commitText(state.original, 1)
                    recordingController.dismiss()
                }
                candidateRefinedRow?.setOnClickListener {
                    currentInputConnection?.commitText(state.refined, 1)
                    recordingController.dismiss()
                }
            }
            is ImeUiState.Error -> {
                timerHandler.removeCallbacks(timerRunnable)
                showStatusRow(state.message, showProgress = false)
                candidateBar?.setOnClickListener { recordingController.dismiss() }
            }
        }
    }

    private fun updateMicAppearance(state: ImeUiState) {
        if (state == ImeUiState.Recording) {
            micButton?.setBackgroundColor(getColor(R.color.mic_active))
            micButton?.let { startMicPulse(it) }
        } else {
            stopMicPulse()
            micButton?.setBackgroundColor(getColor(R.color.mic_idle))
        }
    }

    private fun showStatusRow(
        text: String,
        showProgress: Boolean,
    ) {
        candidateBar?.visibility = View.VISIBLE
        candidateStatusRow?.visibility = View.VISIBLE
        candidateProgress?.visibility = if (showProgress) View.VISIBLE else View.GONE
        candidateText?.text = text
        candidateOriginal?.visibility = View.GONE
        candidateRefinedRow?.visibility = View.GONE
    }

    private fun showDualRows(
        original: String,
        refined: String?,
    ) {
        candidateBar?.visibility = View.VISIBLE
        candidateStatusRow?.visibility = View.GONE
        candidateOriginal?.visibility = View.VISIBLE
        candidateOriginal?.text = original
        candidateRefinedRow?.visibility = View.VISIBLE
        if (refined != null) {
            refineProgress?.visibility = View.GONE
            candidateRefined?.text = refined
        } else {
            refineProgress?.visibility = View.VISIBLE
            candidateRefined?.text = getString(R.string.refining)
        }
    }

    private fun showQuickSettings(anchor: View) {
        serviceScope.launch {
            val currentLang = preferencesManager.languageFlow.first()
            val refinementOn = preferencesManager.refinementEnabledFlow.first()
            val dp = resources.displayMetrics.density

            val container = createQuickSettingsContainer(dp)
            val popup =
                PopupWindow(
                    container,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    true,
                )

            addLanguageOptions(container, popup, currentLang, dp)
            addQuickSettingsDivider(container, dp)
            addRefinementToggle(container, popup, refinementOn, dp)

            popup.showAtLocation(anchor, Gravity.BOTTOM or Gravity.END, (8 * dp).toInt(), (64 * dp).toInt())
        }
    }

    private fun createQuickSettingsContainer(dp: Float): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(resources.getColor(R.color.key_background, null))
            val pad = (12 * dp).toInt()
            setPadding(pad, pad, pad, pad)
        }

    private fun addLanguageOptions(
        container: LinearLayout,
        popup: PopupWindow,
        currentLang: SttLanguage,
        dp: Float,
    ) {
        val languages = listOf(SttLanguage.Auto, SttLanguage.Chinese, SttLanguage.English, SttLanguage.Japanese)
        val langNames =
            listOf(
                getString(R.string.lang_auto),
                getString(R.string.lang_zh),
                getString(R.string.lang_en),
                getString(R.string.lang_ja),
            )
        languages.forEachIndexed { i, lang ->
            val tv =
                TextView(this).apply {
                    text = getString(R.string.quick_language, langNames[i])
                    textSize = 14f
                    setTextColor(
                        if (lang == currentLang) {
                            resources.getColor(R.color.mic_idle, null)
                        } else {
                            resources.getColor(R.color.key_text, null)
                        },
                    )
                    val pad = (8 * dp).toInt()
                    setPadding(pad, pad, pad, pad)
                    setOnClickListener {
                        serviceScope.launch { preferencesManager.setLanguage(lang) }
                        popup.dismiss()
                    }
                }
            container.addView(tv)
        }
    }

    private fun addQuickSettingsDivider(
        container: LinearLayout,
        dp: Float,
    ) {
        val divider =
            View(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (1 * dp).toInt(),
                    ).apply {
                        topMargin = (4 * dp).toInt()
                        bottomMargin = (4 * dp).toInt()
                    }
                setBackgroundColor(0x33FFFFFF)
            }
        container.addView(divider)
    }

    private fun addRefinementToggle(
        container: LinearLayout,
        popup: PopupWindow,
        refinementOn: Boolean,
        dp: Float,
    ) {
        val tv =
            TextView(this).apply {
                text =
                    if (refinementOn) {
                        getString(R.string.quick_refinement_on)
                    } else {
                        getString(R.string.quick_refinement_off)
                    }
                textSize = 14f
                setTextColor(resources.getColor(R.color.key_text, null))
                val pad = (8 * dp).toInt()
                setPadding(pad, pad, pad, pad)
                setOnClickListener {
                    serviceScope.launch { preferencesManager.setRefinementEnabled(!refinementOn) }
                    popup.dismiss()
                }
            }
        container.addView(tv)
    }

    private fun showKeyboardTooltips(rootView: View) {
        val overlay =
            FrameLayout(this).apply {
                setBackgroundColor(0x99000000.toInt())
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
            }

        val tooltips =
            mapOf(
                R.id.btn_switch to getString(R.string.keyboard_switch),
                R.id.btn_backspace to getString(R.string.keyboard_backspace),
                R.id.btn_mic to getString(R.string.keyboard_record),
                R.id.btn_enter to getString(R.string.keyboard_enter),
                R.id.btn_settings to getString(R.string.keyboard_settings),
            )

        rootView.post {
            tooltips.forEach { (btnId, label) ->
                val btn = rootView.findViewById<View>(btnId) ?: return@forEach
                val loc = IntArray(2)
                btn.getLocationInWindow(loc)

                val dp = resources.displayMetrics.density
                val tv =
                    TextView(this).apply {
                        text = label
                        textSize = 11f
                        setTextColor(0xFFFFFFFF.toInt())
                        setBackgroundColor(0xCC6366F1.toInt())
                        val pad = (6 * dp).toInt()
                        setPadding(pad, pad / 2, pad, pad / 2)
                    }

                val params =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        leftMargin = loc[0] + btn.width / 2 - (40 * dp).toInt() / 2
                        topMargin = maxOf(0, loc[1] - (24 * dp).toInt())
                    }
                overlay.addView(tv, params)
            }

            overlay.setOnClickListener {
                (rootView as? ViewGroup)?.removeView(overlay)
            }
            (rootView as? ViewGroup)?.addView(overlay)
        }
    }

    private fun launchSettings() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override fun onDestroy() {
        stopMicPulse()
        timerHandler.removeCallbacks(timerRunnable)
        audioRecorder.release()
        recordingController.destroy()
        serviceScope.cancel()
        super.onDestroy()
    }
}
