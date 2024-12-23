package com.example.beaconble.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.beaconble.AppMain  // This is a singleton class, access it with BeaconReferenceApplication.instance
import com.example.beaconble.BeaconSimplified

@OptIn(ExperimentalStdlibApi::class)
class FragHomeViewModel() : ViewModel() {
    private val appMain = AppMain.instance

    private val _nRangedBeacons = MutableLiveData<Int>()
    val nRangedBeacons: LiveData<Int> get() = _nRangedBeacons
    val rangedBeacons: LiveData<ArrayList<BeaconSimplified>> =
        appMain.loggingSession.beacons
    val isSessionActive: LiveData<Boolean> = appMain.isSessionActive

    init {
        // update the number of beacons detected
        appMain.nRangedBeacons.observeForever { n ->
            _nRangedBeacons.value = n?.toInt()
        }
    }

    fun toggleSession() {
        appMain.toggleSession()
    }

    fun emptyAll() {
        appMain.emptyAll()
    }

    fun uploadAllSessions() {
        appMain.uploadAll()
    }
}
