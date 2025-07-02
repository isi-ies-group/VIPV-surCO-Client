package es.upm.ies.surco.ui

import android.content.Context
import android.graphics.PorterDuff
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import es.upm.ies.surco.R
import es.upm.ies.surco.createPositionMap
import es.upm.ies.surco.databinding.RowItemBeaconBinding
import es.upm.ies.surco.session_logging.BeaconSimplified
import es.upm.ies.surco.session_logging.BeaconSimplifiedStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.toString

class ListAdapterBeacons(
    activityContext: Context,
    private var beaconsList: List<BeaconSimplified>,
    private val lifecycleOwner: LifecycleOwner
) : ArrayAdapter<BeaconSimplified>(activityContext, R.layout.row_item_beacon, beaconsList) {

    private val observers = mutableMapOf<String, Observer<BeaconSimplifiedStatus>>()

    private val positionMap = createPositionMap(context)

    override fun getCount(): Int = beaconsList.size

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val beacon = getItem(position)
        if (beacon == null) {
            Log.e("BeaconListAdapter", "Beacon is null")
        }

        val binding: RowItemBeaconBinding
        val view: View

        if (convertView == null) {
            binding = RowItemBeaconBinding.inflate(LayoutInflater.from(context), parent, false)
            view = binding.root
            view.tag = binding
        } else {
            binding = convertView.tag as RowItemBeaconBinding
            view = convertView
        }

        val beaconVal = beacon ?: return view // If beacon is null, return the view without updating
        // Update the UI elements with beacon data
        binding.tvBeaconIdentifier.text = beaconVal.id.toString()
        binding.tvBeaconLastReading.text = beaconVal.sensorData.value?.lastOrNull()?.data.toString()
        binding.tvBeaconLastSeen.text = beaconVal.sensorData.value?.lastOrNull()?.timestamp?.let {
            timestampFormatter.format(it)  // to local time
        }.orEmpty()
        binding.tvBeaconPosition.text = beaconVal.positionValue.let { unlocalized ->
            // Find key for where value is the unlocalized position string
            positionMap.entries.find { it.value == unlocalized }?.key
                ?: context.getString(R.string.beacon_detail_position_unknown)
        }
        binding.tvBeaconTilt.text = beaconVal.tiltValue.let {
            if (it == null) {
                context.getString(R.string.no_degrees)
            } else {
                context.getString(R.string.any_degrees_format, it.toInt().toString())
                }
        }
        binding.tvBeaconInfoIncomplete.visibility =
            if (beaconVal.statusValue.value != BeaconSimplifiedStatus.INFO_MISSING) View.GONE else View.VISIBLE

        // Remove existing observer if any
        observers[beaconVal.id.toString()]?.let {
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
                    R.drawable.check, R.color.green_ok, context.getString(R.string.beacon_detail_ok)
                )  // OK
            }
            binding.ivBeaconStatus.setImageResource(imageResource)
            binding.ivBeaconStatus.setColorFilter(
                context.getColor(tintColor), PorterDuff.Mode.SRC_IN
            )
            binding.ivBeaconStatus.contentDescription = contentDescription
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
        val timestampFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
    }
}
