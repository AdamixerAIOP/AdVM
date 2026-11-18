package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.WorkstationRepository
import com.example.data.model.FormFactor
import com.example.data.model.HostHardwareReport
import com.example.data.model.UiState
import com.example.data.model.UserSyncProfile
import com.example.data.model.VmProfile
import com.example.data.model.VmSnapshot
import com.example.data.model.VmStatus
import com.example.service.HostCapabilityProbe
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val repository: WorkstationRepository,
    private val currentUserId: String,
    initialHardwareReport: HostHardwareReport,
    private val isGuestMode: Boolean = false
) : ViewModel() {

    private val _hostReport = MutableStateFlow(initialHardwareReport)
    val hostReport: StateFlow<HostHardwareReport> = _hostReport.asStateFlow()

    private val _selectedFormFactorFilter = MutableStateFlow<FormFactor?>(null)
    val selectedFormFactorFilter: StateFlow<FormFactor?> = _selectedFormFactorFilter.asStateFlow()

    private val _selectedVmId = MutableStateFlow<String?>(null)
    val selectedVmId: StateFlow<String?> = _selectedVmId.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    val vmsState: StateFlow<UiState<List<VmProfile>>> = repository.observeVms(currentUserId)
        .map<List<VmProfile>, UiState<List<VmProfile>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing VMs", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load virtual machines"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val userProfileState: StateFlow<UiState<UserSyncProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserSyncProfile?, UiState<UserSyncProfile?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing user sync profile", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load cloud sync profile"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val snapshotsState: StateFlow<UiState<List<VmSnapshot>>> = repository.observeSnapshots(currentUserId)
        .map<List<VmSnapshot>, UiState<List<VmSnapshot>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing snapshots", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load snapshots"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    fun refreshHostProbe(appContext: Context) {
        _hostReport.value = HostCapabilityProbe.inspect(appContext.applicationContext)
        _statusBanner.value = "Hardware capability probe refreshed (${_hostReport.value.avfStatusLabel})"
    }

    fun setFormFactorFilter(filter: FormFactor?) {
        _selectedFormFactorFilter.value = filter
        if (filter != null) {
            viewModelScope.launch {
                repository.updatePreferredFormFactor(currentUserId, filter)
            }
        }
    }

    fun selectVm(vmId: String?) {
        _selectedVmId.value = vmId
    }

    fun saveVm(vm: VmProfile, onComplete: () -> Unit = {}) {
        if (vm.name.isBlank() || vm.name.length > 60) {
            _statusBanner.value = "VM name must be 1 to 60 characters."
            return
        }
        if (vm.vncPort !in 1..65535) {
            _statusBanner.value = "VNC port must be between 1 and 65535."
            return
        }
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.saveVm(currentUserId, vm)
            _isMutating.value = false
            result.onSuccess { newId ->
                _selectedVmId.value = newId
                _statusBanner.value = if (isGuestMode) {
                    "Saved \"${vm.name}\" locally (cloud sync off)."
                } else {
                    "Synced \"${vm.name}\" to cloud profile."
                }
                onComplete()
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Failed to save VM configuration."
            }
        }
    }

    fun startVm(vm: VmProfile) {
        if (vm.parsedStatus == VmStatus.RUNNING) return
        viewModelScope.launch {
            repository.updateVmStatus(currentUserId, vm.id, VmStatus.RUNNING)
        }
    }

    fun toggleVmPower(vm: VmProfile) {
        val nextStatus = when (vm.parsedStatus) {
            VmStatus.STOPPED, VmStatus.PAUSED -> VmStatus.RUNNING
            VmStatus.RUNNING -> VmStatus.PAUSED
        }
        viewModelScope.launch {
            val result = repository.updateVmStatus(currentUserId, vm.id, nextStatus)
            result.onSuccess {
                _statusBanner.value = "${vm.name} marked ${nextStatus.label}."
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Could not update VM power state."
            }
        }
    }

    fun stopVm(vm: VmProfile) {
        viewModelScope.launch {
            repository.updateVmStatus(currentUserId, vm.id, VmStatus.STOPPED)
                .onSuccess { _statusBanner.value = "${vm.name} powered off." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to power off VM." }
        }
    }

    fun deleteVm(vm: VmProfile) {
        viewModelScope.launch {
            repository.deleteVm(currentUserId, vm.id)
                .onSuccess {
                    if (_selectedVmId.value == vm.id) _selectedVmId.value = null
                    _statusBanner.value = "Removed \"${vm.name}\"."
                }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to delete VM." }
        }
    }

    fun createSnapshot(vm: VmProfile, label: String) {
        viewModelScope.launch {
            repository.createSnapshot(currentUserId, vm, label)
                .onSuccess {
                    _statusBanner.value = if (isGuestMode) {
                        "Local snapshot \"$label\" saved (cloud sync off)."
                    } else {
                        "Cloud snapshot \"$label\" created for ${vm.name}."
                    }
                }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Snapshot failed." }
        }
    }

    fun deleteSnapshot(snapshotId: String) {
        viewModelScope.launch {
            repository.deleteSnapshot(currentUserId, snapshotId)
                .onSuccess { _statusBanner.value = "Snapshot removed from cloud." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to remove snapshot." }
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    private companion object {
        const val TAG = "DashboardVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
