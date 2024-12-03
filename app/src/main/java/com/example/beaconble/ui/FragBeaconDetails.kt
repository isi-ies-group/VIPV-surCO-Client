package com.example.beaconble.ui

import android.app.AlertDialog
import androidx.fragment.app.viewModels
import android.os.Bundle
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.beaconble.R

class FragBeaconDetails : Fragment() {
    private val viewModel: FragBeaconDetailsViewModel by viewModels()

    lateinit var textViewBeaconId: TextView
    lateinit var editTextBeaconDescription: EditText
    lateinit var editTextBeaconTilt: EditText
    lateinit var editTextBeaconDirection: EditText
    lateinit var listViewBeaconMeasurements: ListView
    lateinit var deleteBeaconButton: ImageButton

    lateinit var adapter: ListAdapterSensorEntries

    val textWatcher4AllFields: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            // do nothing
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            // do nothing
        }

        override fun afterTextChanged(s: android.text.Editable?) {
            // call the updateBeaconFields method
            viewModel.updateBeacon4Fields(
                editTextBeaconDescription.text.toString(),
                editTextBeaconTilt.text.toString().toFloatOrNull(),
                editTextBeaconDirection.text.toString().toFloatOrNull()
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (arguments != null) {
            val beaconId = arguments?.getString("beaconId")
            viewModel.loadBeacon(beaconId)
        }
        activity?.title = "Beacon Details"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment and find the IDs of the UI elements.
        val view = inflater.inflate(R.layout.fragment_beacon_details, container, false)

        textViewBeaconId = view.findViewById<TextView>(R.id.tvBeaconIdentifier)
        editTextBeaconDescription = view.findViewById<EditText>(R.id.editTextDescription)
        editTextBeaconTilt = view.findViewById<EditText>(R.id.editTextTilt)
        editTextBeaconDirection = view.findViewById<EditText>(R.id.editTextDirection)
        listViewBeaconMeasurements = view.findViewById<ListView>(R.id.listViewSensorEntries)
        deleteBeaconButton = view.findViewById<ImageButton>(R.id.imageButtonDeleteBeacon)

        adapter = ListAdapterSensorEntries(requireContext(), ArrayList())
        listViewBeaconMeasurements.adapter = adapter

        // Assign observers and callbacks to the ViewModel's LiveData objects.
        viewModel.beacon.observe(viewLifecycleOwner) { beacon ->
            if (beacon != null) {
                updateTextFields()
            } else {
                // If the beacon is null, go back to the previous fragment,
                // which is almost certainly due to the beacon being deleted.
                activity?.onBackPressedDispatcher?.onBackPressed()
            }
        }

        viewModel.sensorEntries.observe(viewLifecycleOwner) { sensorEntries ->
            adapter.updateData(sensorEntries.asReversed())
        }

        // Set the delete button callback
        deleteBeaconButton.setOnClickListener {
            // Create alertDialog to confirm the action
            val alertDialog = AlertDialog.Builder(requireContext())
            alertDialog.setTitle(getString(R.string.empty_all_data))
            alertDialog.setMessage(getString(R.string.empty_all_data_confirmation))
            alertDialog.setPositiveButton(getString(R.string.yes)) { dialog, which ->
                viewModel.deleteBeacon()
            }
            alertDialog.setNegativeButton(getString(R.string.no)) { dialog, which ->
            }
            alertDialog.show()
        }

        // Return the view.
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Update text fields with the beacon's data.
        updateTextFields()

        // Set callbacks / text watchers for the text fields AFTER the view texts have been set.
        editTextBeaconDescription.addTextChangedListener(textWatcher4AllFields)
        editTextBeaconTilt.addTextChangedListener(textWatcher4AllFields)
        editTextBeaconDirection.addTextChangedListener(textWatcher4AllFields)
    }

    fun updateTextFields() {
        // Update text fields with the beacon's data.
        viewModel.beacon.value?.let {
            textViewBeaconId.text = it.id.toString()
            editTextBeaconDescription.setText(it.description)

            editTextBeaconTilt.setText(if (it.tilt != null) it.tilt.toString() else "")
            editTextBeaconDirection.setText(if (it.direction != null) it.direction.toString() else "")
        }
    }
}