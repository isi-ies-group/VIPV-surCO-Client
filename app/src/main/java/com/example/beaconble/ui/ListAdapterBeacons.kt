package com.example.beaconble.ui

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.ArrayAdapter
import android.widget.ImageView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.example.beaconble.BeaconSimplified
import com.example.beaconble.BeaconSimplifiedStatus
import com.example.beaconble.R
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ListAdapterBeacons(
    activityContext: Context,
    private var beaconsList: List<BeaconSimplified>,
    private val lifecycleOwner: LifecycleOwner
) : ArrayAdapter<BeaconSimplified>(activityContext, R.layout.row_item_beacon, beaconsList) {

    private val observers = mutableMapOf<String, Observer<BeaconSimplifiedStatus>>()

    override fun getCount(): Int = beaconsList.size

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
        val ivBeaconStatus = view.findViewById<ImageView>(R.id.ivBeaconStatus)
        val tvBeaconInfoIncomplete = view.findViewById<TextView>(R.id.tvBeaconInfoIncomplete)

        beaconIdTextView.text = beacon?.id.toString()
        beaconLastReadingTextView.text = beacon?.sensorData?.value?.lastOrNull()?.data.toString()
        beaconLastSeenTextView.text = beacon?.sensorData?.value?.lastOrNull()?.timestamp?.let {
            timestampFormatter.format(it)  // to local time
        }.orEmpty()
        tvBeaconInfoIncomplete.visibility =
            if (beacon?.statusValue?.value != BeaconSimplifiedStatus.INFO_MISSING) View.GONE else View.VISIBLE

        // Remove existing observer if any
        observers[beacon!!.id.toString()]?.let {
            beacon.statusValue.removeObserver(it)
        }

        val observer = Observer<BeaconSimplifiedStatus> { status ->
            val (imageResource, tintColor, contentDescription) = when (status) {
                BeaconSimplifiedStatus.OFFLINE -> Triple(
                    R.drawable.bluetooth_off,
                    R.color.warning_red,
                    context.getString(R.string.beacon_detail_out_of_range_error)
                )

                BeaconSimplifiedStatus.INFO_MISSING -> Triple(
                    R.drawable.warning,
                    R.color.warning_orange,
                    context.getString(R.string.beacon_detail_info_error)
                )

                else -> Triple(
                    R.drawable.check,
                    R.color.green_ok,
                    context.getString(R.string.beacon_detail_ok)
                )  // OK
            }
            ivBeaconStatus.setImageResource(imageResource)
            ivBeaconStatus.setColorFilter(
                context.getColor(tintColor), android.graphics.PorterDuff.Mode.SRC_IN
            )
            ivBeaconStatus.contentDescription = contentDescription
        }

        // Add new observer
        beacon.statusValue.observe(lifecycleOwner, observer)
        observers[beacon.id.toString()] = observer

        return view
    }

    /**
     * Updates the data in the adapter.
     * @param beacons The new list of beacons.
     */
    fun updateData(beacons: List<BeaconSimplified>) {
        beaconsList = beacons
        clear()
        addAll(beacons)
        notifyDataSetChanged()
    }

    companion object {
        val timestampFormatter =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
    }
}
