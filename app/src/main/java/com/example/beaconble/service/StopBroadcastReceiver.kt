package com.example.beaconble.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.beaconble.AppMain
import com.example.beaconble.AppMain.Companion.TAG


/**
 * Class receiver to stop the beacon scanning session
 */
class StopBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.i(TAG, "Received stop signal on broadcast receiver")
        AppMain.instance.concludeSession()
    }
}
