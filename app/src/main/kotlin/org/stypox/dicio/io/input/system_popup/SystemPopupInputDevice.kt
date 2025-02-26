package org.stypox.dicio.io.input.system_popup

import android.app.Activity.RESULT_OK
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.result.ActivityResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.stypox.dicio.R
import org.stypox.dicio.di.ActivityForResultManager
import org.stypox.dicio.di.LocaleManager
import org.stypox.dicio.io.input.InputEvent
import org.stypox.dicio.io.input.SttInputDevice
import org.stypox.dicio.io.input.SttState
import org.stypox.dicio.io.input.stt_popup.SttPopupActivity
import org.stypox.dicio.util.distinctUntilChangedBlockingFirst
import java.util.Locale


class SystemPopupInputDevice(
    @ApplicationContext val context: Context,
    private val activityForResultManager: ActivityForResultManager,
    localeManager: LocaleManager,
) : SttInputDevice {

    private var locale: Locale

    private val _state: MutableStateFlow<SystemPopupState>
    private val _uiState: MutableStateFlow<SttState>
    override val uiState: StateFlow<SttState>

    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        // Run blocking, because the locale is always available right away since LocaleManager also
        // initializes in a blocking way.
        val (firstLocale, nextLocaleFlow) = localeManager.locale
            .distinctUntilChangedBlockingFirst()
        locale = firstLocale

        val initialState = stateFromResolveActivity()
        _state = MutableStateFlow(initialState)
        _uiState = MutableStateFlow(initialState.toUiState())
        uiState = _uiState

        scope.launch {
            _state.collect { _uiState.value = it.toUiState() }
        }

        scope.launch {
            // perform initialization again every time the locale changes
            nextLocaleFlow.collect { newLocale ->
                locale = newLocale
                _state.emit(stateFromResolveActivity())
            }
        }
    }

    override fun tryLoad(thenStartListeningEventListener: ((InputEvent) -> Unit)?): Boolean {
        if (thenStartListeningEventListener != null) {
            return startListening(thenStartListeningEventListener)
        }
        return when (_state.value) {
            SystemPopupState.NotAvailable, is SystemPopupState.ErrorStartingActivity -> false
            else -> true
        }
    }

    override fun stopListening() {
        // no way to implement
    }

    override fun onClick(eventListener: (InputEvent) -> Unit) {
        startListening(eventListener)
    }

    override suspend fun destroy() {
        scope.cancel()
    }

    private fun getIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.stt_say_something))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // otherwise Dicio would use itself :-D
                putExtra(
                    Intent.EXTRA_EXCLUDE_COMPONENTS,
                    arrayOf(ComponentName(context, SttPopupActivity::class.java))
                )
            }
        }
    }

    private fun stateFromResolveActivity(): SystemPopupState {
        return if (getIntent().resolveActivity(context.packageManager) == null) {
            SystemPopupState.NotAvailable
        } else {
            SystemPopupState.Available
        }
    }

    private fun startListening(eventListener: (InputEvent) -> Unit): Boolean {
        if (_state.compareAndSet(
            SystemPopupState.Available,
            SystemPopupState.Listening(eventListener)
        ) || _state.compareAndSet(
            SystemPopupState.ErrorStartingActivity(Throwable()),
            SystemPopupState.Listening(eventListener)
        ) || _state.compareAndSet(
            SystemPopupState.ErrorActivityResult(0),
            SystemPopupState.Listening(eventListener)
        )) {
            try {
                activityForResultManager.launch(getIntent(), this::onActivityResult)
            } catch (e: Throwable) {
                Log.e(TAG, "Could not start STT activity", e)
                _state.compareAndSet(
                    SystemPopupState.Listening { },
                    SystemPopupState.ErrorStartingActivity(e)
                )
            }
        }
        return false
    }

    private fun onActivityResult(result: ActivityResult) {
        // all activity requesters are used just once since the activity might change
        val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val confidences = result.data?.getFloatArrayExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES)
        val eventListener = _state.value as? SystemPopupState.Listening ?: return

        if (result.resultCode == RESULT_OK && !results.isNullOrEmpty()) {
            // this is not atomic but there is no alternative in Kotlin to compare, update and get
            // the previous value
            _state.compareAndSet(
                SystemPopupState.Listening { },
                SystemPopupState.Available
            )

            if (results.size == confidences?.size) {
                eventListener.listener(InputEvent.Final(results.zip(confidences.map { it })))
            } else {
                eventListener.listener(InputEvent.Final(results.map { Pair(it, 1.0f) }))
            }

        } else {
            _state.compareAndSet(
                SystemPopupState.Listening { },
                SystemPopupState.ErrorActivityResult(result.resultCode)
            )

            eventListener.listener(InputEvent.Error(ResultCodeException(result.resultCode)))
        }
    }

    companion object {
        val TAG = SystemPopupInputDevice::class.simpleName
    }
}