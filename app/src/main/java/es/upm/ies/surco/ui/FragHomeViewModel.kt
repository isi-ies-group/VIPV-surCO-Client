package es.upm.ies.surco.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import es.upm.ies.surco.AppMain
import es.upm.ies.surco.session_logging.BeaconSimplified
import es.upm.ies.surco.session_logging.LoggingSessionStatus

@OptIn(ExperimentalStdlibApi::class)
class FragHomeViewModel() : ViewModel() {
    private val appMain = AppMain.Companion.instance

    val nBeaconsOnline: LiveData<Int> = appMain.loggingSession.nBeaconsOnline
    val rangedBeacons: LiveData<ArrayList<BeaconSimplified>> = appMain.loggingSession.beacons
    val loggingSessionStatus: LiveData<LoggingSessionStatus> = appMain.loggingSession.status

    fun toggleSession() {
        appMain.toggleSession()
    }

    fun uploadAllSessions() {
        appMain.uploadAll()
    }
}
