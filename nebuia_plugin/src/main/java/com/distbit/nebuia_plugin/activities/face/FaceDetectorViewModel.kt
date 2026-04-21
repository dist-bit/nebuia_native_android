package com.distbit.nebuia_plugin.activities.face

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FaceDetectorViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<FaceDetectorUiState>(FaceDetectorUiState.Initial)
    val uiState: StateFlow<FaceDetectorUiState> = _uiState.asStateFlow()

    sealed class FaceDetectorUiState {
        object Initial : FaceDetectorUiState()
        object Processing : FaceDetectorUiState()
        object FaceDetected : FaceDetectorUiState()
        object IdFrontDetected : FaceDetectorUiState()
        object IdBackDetected : FaceDetectorUiState()
        object Complete : FaceDetectorUiState()
        data class Error(val message: String) : FaceDetectorUiState()
    }

    fun updateState(newState: FaceDetectorUiState) {
        viewModelScope.launch {
            _uiState.emit(newState)
        }
    }
}