package es.upm.ies.vipvble.ui

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import es.upm.ies.vipvble.R
import es.upm.ies.vipvble.SensorEntry
import es.upm.ies.vipvble.databinding.RowItemDataLogBinding
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

        val binding: RowItemDataLogBinding
        val view: View

        if (convertView == null) {
            binding = RowItemDataLogBinding.inflate(LayoutInflater.from(context), parent, false)
            view = binding.root
            view.tag = binding
        } else {
            binding = convertView.tag as RowItemDataLogBinding
            view = convertView
        }

        binding.tvTimestamp.text = dataEntry?.timestamp?.let {
            timestampFormatter.format(it)  // to local time
        }.orEmpty()
        binding.tvMeasurement.text = dataEntry?.data.toString()

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

    companion object {
        /**
         * Formatter for the timestamp, from Instant to local time.
         */
        val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            .withZone(ZoneId.systemDefault())
    }
}
