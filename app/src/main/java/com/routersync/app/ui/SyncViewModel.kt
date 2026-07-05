package com.routersync.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.routersync.app.data.SyncProfile
import com.routersync.app.data.SyncProfileRepository
import com.routersync.app.sync.FreeSpaceResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SyncViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SyncProfileRepository(application)

    val profiles = repository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _freeingSpaceProfileId = MutableStateFlow<Long?>(null)
    val freeingSpaceProfileId: StateFlow<Long?> = _freeingSpaceProfileId

    private val _freeSpaceResult = MutableStateFlow<FreeSpaceResult?>(null)
    val freeSpaceResult: StateFlow<FreeSpaceResult?> = _freeSpaceResult

    fun saveProfile(profile: SyncProfile) = viewModelScope.launch {
        repository.saveProfile(profile)
    }

    fun deleteProfile(profile: SyncProfile) = viewModelScope.launch {
        repository.deleteProfile(profile)
    }

    fun runManualSync(profile: SyncProfile) = repository.runManualSync(profile)

    fun cancelSync(profile: SyncProfile) = repository.cancelSync(profile)

    fun freeLocalSpace(profile: SyncProfile) {
        viewModelScope.launch {
            _freeingSpaceProfileId.value = profile.id
            val result = try {
                repository.freeLocalSpace(profile)
            } catch (e: Exception) {
                FreeSpaceResult(false, "Errore: ${e.message}", 0, 0)
            }
            _freeingSpaceProfileId.value = null
            _freeSpaceResult.value = result
        }
    }

    fun clearFreeSpaceResult() {
        _freeSpaceResult.value = null
    }
}
