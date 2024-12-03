package com.example.beaconble.ui

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.ArrayAdapter
import com.example.beaconble.SensorEntry
import com.example.beaconble.R

/**
 * Adapter for the list of logged sensor entries.
 * @param context The context of the activity.
 * @param layout The layout of the list item.
 * @param beaconsList The list of beacons to be displayed.
 */
class ListAdapterSensorEntries(activityContext: Context, beaconsList: List<SensorEntry>) :
    ArrayAdapter<SensorEntry>(activityContext, R.layout.row_item_data_log, beaconsList) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val dataEntry = getItem(position)
        if (dataEntry == null) {
            Log.e("ListAdapterSensorEntries", "Data is null")
        }
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.row_item_data_log, parent, false)

        val textViewTimestamp = view.findViewById<TextView>(R.id.tvTimestamp)
        val textViewMeasurement = view.findViewById<TextView>(R.id.tvMeasurement)

        textViewTimestamp.text = dataEntry?.timestamp.toString()
        textViewMeasurement.text = dataEntry?.data.toString()

        return view
    }

    /**
     * Updates the data in the adapter.
     * @param sensorEntries The new list of sensor entries.
     */
    fun updateData(sensorEntries: List<SensorEntry>) {
        clear()
        addAll(sensorEntries)
        notifyDataSetChanged()
    }
}
