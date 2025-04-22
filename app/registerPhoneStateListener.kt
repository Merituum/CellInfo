
import android.R
import android.content.Context
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.widget.TextView

private fun registerPhoneStateListener() {
    val telephonyManager: TelephonyManager =
        getSystemService(android.content.Context.TELEPHONY_SERVICE) as TelephonyManager
    val phoneStateListener: PhoneStateListener = object : PhoneStateListener() {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            super.onSignalStrengthsChanged(signalStrength)
            updateSignalStrengthInfo(signalStrength)
        }
    }
    telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
}

private fun updateSignalStrengthInfo(signalStrength: SignalStrength) {
    // Aktualizuj informacje o sile sygnału w interfejsie użytkownika
    val dbm: Int = cellTowerDetector.getSignalStrength()
    val signalTextView: android.widget.TextView = findViewById(R.id.signal_strength)
    signalTextView.setText("Siła sygnału: " + dbm + " dBm")


    // Aktualizuj marker połączonego nadajnika
    if (currentConnectedMarker != null) {
        currentConnectedMarker.setSnippet("Siła sygnału: " + dbm + " dBm")
        if (currentConnectedMarker.isInfoWindowShown()) {
            currentConnectedMarker.hideInfoWindow()
            currentConnectedMarker.showInfoWindow()
        }
    }
}
