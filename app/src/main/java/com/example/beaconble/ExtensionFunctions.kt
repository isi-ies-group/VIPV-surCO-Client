package com.example.beaconble

import androidx.lifecycle.MutableLiveData

/**
 * Notifies the observers of a MutableLiveData.
 */
fun <T> MutableLiveData<T>.notifyObservers() {
    this.value = this.value
}
