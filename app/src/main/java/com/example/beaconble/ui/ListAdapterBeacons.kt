package com.example.beaconble.ui

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.ArrayAdapter
import com.example.beaconble.BeaconSimplified
import com.example.beaconble.R
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Adapter for the list of beacons.
 * @param context The context of the activity.
 * @param layout The layout of the list item.
 * @param beaconsList The list of beacons to be displayed.
 */
class ListAdapterBeacons(activityContext: Context, beaconsList: List<BeaconSimplified>) :
    ArrayAdapter<BeaconSimplified>(activityContext, R.layout.row_item_beacon, beaconsList) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val beacon = getItem(position)
        if (beacon == null) {
            Log.e("BeaconListAdapter", "Beacon is null")
        }
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.row_item_beacon, parent, false)

        val beaconIdTextView = view.findViewById<TextView>(R.id.tvBeaconIdentifier)
        val beaconLastReadingTextView = view.findViewById<TextView>(R.id.tvBeaconLastReading)
        val beaconLastSeenTextView = view.findViewById<TextView>(R.id.tvBeaconLastSeen)

        beaconIdTextView.text = beacon?.id.toString()
        beaconLastReadingTextView.text = beacon?.sensorData?.value?.lastOrNull()?.data.toString()
        beaconLastSeenTextView.text = beacon?.sensorData?.value?.lastOrNull()?.timestamp?.let {
            timestampFormatter.format(it)  // to local time
        }.orEmpty()

        return view
    }

    /**
     * Updates the data in the adapter.
     * @param beacons The new list of beacons.
     */
    fun updateData(beacons: List<BeaconSimplified>) {
        clear()
        addAll(beacons)
        notifyDataSetChanged()
    }

    companion object {
        val timestampFormatter =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
    }
}
