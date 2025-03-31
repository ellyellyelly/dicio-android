package org.stypox.dicio.ui.home

import android.app.Application
import androidx.compose.material3.SnackbarHostState
import androidx.datastore.core.DataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.dicio.skill.context.SkillContext
import org.stypox.dicio.di.SkillContextInternal
import org.stypox.dicio.di.SpeechOutputDeviceWrapper
import org.stypox.dicio.di.SttInputDeviceWrapper
import org.stypox.dicio.di.WakeDeviceWrapper
import org.stypox.dicio.eval.SkillEvaluator
import org.stypox.dicio.eval.SkillHandler
import org.stypox.dicio.io.input.SttState
import org.stypox.dicio.io.speech.SnackbarSpeechDevice
import org.stypox.dicio.settings.datastore.UserSettings
import org.stypox.dicio.settings.datastore.WakeDevice
import org.stypox.dicio.settings.datastore.copy
import javax.inject.Inject

@HiltViewModel
class HomeScreenViewModel @Inject constructor(
    application: Application,
    val skillContext: SkillContextInternal,
    val skillHandler: SkillHandler,
    val dataStore: DataStore<UserSettings>,
    val sttInputDevice: SttInputDeviceWrapper,
    val speechOutputDevice: SpeechOutputDeviceWrapper,
    val wakeDevice: WakeDeviceWrapper,
    val skillEvaluator: SkillEvaluator,
    // this is always instantiated, but will do nothing if
    // it is not the speech device chosen by the user
    snackbarSpeechDevice: SnackbarSpeechDevice,
) : AndroidViewModel(application) {

    private var showSnackbarJob: Job? = null
    val snackbarHostState = SnackbarHostState()

    init {
        // show snackbars generated by the SnackbarSpeechDevice
        viewModelScope.launch {
            snackbarSpeechDevice.events.collect {
                if (it == null) {
                    // "stop speaking", i.e. remove the current snackbar
                    showSnackbarJob?.cancel()
                } else {
                    // replace the current snackbar
                    showSnackbarJob?.cancel()
                    showSnackbarJob = launch {
                        snackbarHostState.showSnackbar(it)
                    }
                }
            }
        }

        // stop speaking when the STT device starts listening
        viewModelScope.launch {
            sttInputDevice.uiState
                .filter { it == SttState.Listening }
                .collect { speechOutputDevice.stopSpeaking() }
        }
    }

    fun disableWakeWord() {
        viewModelScope.launch {
            dataStore.updateData {
                it.copy { wakeDevice = WakeDevice.WAKE_DEVICE_NOTHING }
            }
        }
    }
}
