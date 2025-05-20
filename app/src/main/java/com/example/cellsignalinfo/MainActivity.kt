package com.example.cellsignalinfo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.telephony.*
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var tvRSRP: TextView
    private lateinit var tvRSRQ: TextView
    private lateinit var tvSINR: TextView
    private lateinit var tvCellId: TextView
    private var userMarker: Marker? = null // Marker dla lokalizacji użytkownika

    private val PERMISSION_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Konfiguracja OSM przed setContentView
        val ctx = applicationContext
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.activity_main)

        // Inicjalizacja widoków
        tvRSRP = findViewById(R.id.tv_rsrp)
        tvRSRQ = findViewById(R.id.tv_rsrq)
        tvSINR = findViewById(R.id.tv_sinr)
        tvCellId = findViewById(R.id.tv_cell_id)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Inicjalizacja mapy OSM
        mapView = findViewById(R.id.map)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        val mapController = mapView.controller
        mapController.setZoom(15.0)

        // Żądanie uprawnień
        requestPermissions()
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                getUserLocation()
                getCellularInfo()
            } else {
                Log.e("Permissions", "Nie wszystkie uprawnienia zostały przyznane.")
            }
        }
    }

    private fun getCellularInfo() {
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e("CellularInfo", "Brak uprawnień do lokalizacji.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            telephonyManager.requestCellInfoUpdate(mainExecutor, object : TelephonyManager.CellInfoCallback() {
                override fun onCellInfo(cellInfoList: List<CellInfo>) {
                    for (cellInfo in cellInfoList) {
                        when (cellInfo) {
                            is CellInfoLte -> {
                                val cellIdentity = cellInfo.cellIdentity
                                val signalStrength = cellInfo.cellSignalStrength as CellSignalStrengthLte

                                val rsrp = signalStrength.rsrp
                                val rsrq = signalStrength.rsrq
                                val rssnr = signalStrength.rssnr
                                val cellId = cellIdentity.ci

                                // Aktualizacja UI
                                tvRSRP.text = "RSRP: $rsrp dBm"
                                tvRSRQ.text = "RSRQ: $rsrq dB"
                                tvSINR.text = "SINR: $rssnr dB"
                                tvCellId.text = "Cell ID: $cellId"
                                Log.d("CellularInfo", "RSRP: $rsrp dBm, Cell ID: $cellId")
                                break
                            }
                        }
                    }
                }
            })
        }
    }

    private fun getUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e("Location", "Brak uprawnień do lokalizacji.")
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val currentLocation = GeoPoint(it.latitude, it.longitude)
                updateUserMarker(currentLocation)
                mapView.controller.setCenter(currentLocation)
                mapView.controller.setZoom(15.0)
                mapView.invalidate()
                Log.d("Location", "Lokalizacja zaktualizowana: ${it.latitude}, ${it.longitude}")
                getCellularInfo() // Aktualizacja informacji o komórce po uzyskaniu lokalizacji
            } ?: run {
                Log.e("Location", "Nie udało się pobrać lokalizacji.")
            }
        }.addOnFailureListener { exception ->
            Log.e("Location", "Błąd podczas pobierania lokalizacji: ${exception.message}")
        }
    }

    private fun updateUserMarker(location: GeoPoint) {
        // Jeśli marker już istnieje, aktualizuj jego pozycję
        if (userMarker != null) {
            userMarker?.position = location
        } else {
            // Jeśli marker nie istnieje, utwórz nowy
            userMarker = Marker(mapView).apply {
                position = location
                title = "Twoja lokalizacja"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(userMarker)
        }
        mapView.invalidate() // Odśwież mapę
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        // Ponowne żądanie lokalizacji przy wznowieniu aktywności
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getUserLocation()
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}
