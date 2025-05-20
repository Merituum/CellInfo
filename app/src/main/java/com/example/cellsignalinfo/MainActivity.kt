package com.example.cellsignalinfo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.preference.PreferenceManager
import android.provider.Settings
import android.telephony.*
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
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
    private var userMarker: Marker? = null
    private val cellTowerMarkers = mutableListOf<Marker>()

    private var locationCallback: LocationCallback? = null
    private var locationRequest: LocationRequest? = null
    private lateinit var telephonyManager: TelephonyManager

    // Dla API Android 12+
    private var telephonyCallback: TelephonyCallback? = null

    private var permissionsGranted = false
    private val PERMISSION_REQUEST_CODE = 123

    companion object {
        private const val TAG = "MainActivity"
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Konfiguracja OSM
            val ctx = applicationContext
            Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
            Configuration.getInstance().userAgentValue = packageName
        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas konfiguracji OSM: ${e.message}")
        }

        setContentView(R.layout.activity_main)

        // Inicjalizacja widoków
        tvRSRP = findViewById(R.id.tv_rsrp)
        tvRSRQ = findViewById(R.id.tv_rsrq)
        tvSINR = findViewById(R.id.tv_sinr)
        tvCellId = findViewById(R.id.tv_cell_id)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        try {
            // Inicjalizacja mapy OSM
            mapView = findViewById(R.id.map)
            mapView.setTileSource(TileSourceFactory.MAPNIK)
            mapView.setMultiTouchControls(true)
            mapView.controller.setZoom(15.0)
        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas inicjalizacji mapy: ${e.message}")
        }

        // Żądanie uprawnień
        requestPermissions()
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Sprawdź czy wszystkie uprawnienia zostały przyznane
            permissionsGranted = grantResults.isNotEmpty() &&
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (permissionsGranted) {
                Log.d(TAG, "Wszystkie uprawnienia zostały przyznane")
                setupLocationUpdates()
                setupTelephonyCallback()
                getUserLocation() // Inicjalna lokalizacja
            } else {
                Log.e(TAG, "Nie wszystkie uprawnienia zostały przyznane")
                // Sprawdź które uprawnienia są brakujące
                val missingPermissions = permissions.filterIndexed { index, _ ->
                    grantResults.getOrNull(index) != PackageManager.PERMISSION_GRANTED
                }
                Log.e(TAG, "Brakujące uprawnienia: $missingPermissions")

                // Pokaż wyjaśnienie
                showPermissionRationale()
            }
        }
    }

    private fun showPermissionRationale() {
        val shouldShowRationale = REQUIRED_PERMISSIONS.any {
            shouldShowRequestPermissionRationale(it)
        }

        if (shouldShowRationale) {
            // Użytkownik odrzucił, ale nie zaznaczył "nie pytaj ponownie"
            AlertDialog.Builder(this)
                .setTitle("Wymagane uprawnienia")
                .setMessage("Aplikacja potrzebuje dostępu do lokalizacji i stanu telefonu, aby pokazać informacje o sieci komórkowej i twoją pozycję na mapie.")
                .setPositiveButton("Przyznaj uprawnienia") { _, _ ->
                    requestPermissions()
                }
                .setNegativeButton("Anuluj") { _, _ ->
                    Toast.makeText(this, "Aplikacja nie będzie działać prawidłowo bez wymaganych uprawnień", Toast.LENGTH_LONG).show()
                }
                .setCancelable(false)
                .show()
        } else {
            // Użytkownik zaznaczył "nie pytaj ponownie" - skieruj do ustawień
            AlertDialog.Builder(this)
                .setTitle("Uprawnienia wymagane")
                .setMessage("Wymagane uprawnienia zostały trwale odrzucone. Przejdź do ustawień aplikacji, aby włączyć je ręcznie.")
                .setPositiveButton("Otwórz ustawienia") { _, _ ->
                    openAppSettings()
                }
                .setNegativeButton("Anuluj") { _, _ ->
                    Toast.makeText(this, "Aplikacja nie będzie działać prawidłowo bez wymaganych uprawnień", Toast.LENGTH_LONG).show()
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun openAppSettings() {
        val intent = Intent().apply {
            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    // Metoda sprawdzająca uprawnienia lokalizacji
    private fun hasLocationPermissions(): Boolean {
        return (ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED)
    }

    // Metoda sprawdzająca uprawnienia telefonu
    private fun hasPhoneStatePermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Konfiguracja słuchacza aktualizacji lokalizacji
    private fun setupLocationUpdates() {
        try {
            locationRequest = LocationRequest.Builder(5000) // 5 sekund
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMinUpdateIntervalMillis(3000) // 3 sekundy
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        val currentLocation = GeoPoint(location.latitude, location.longitude)
                        updateUserMarker(currentLocation)
                        Log.d(TAG, "Aktualizacja lokalizacji: ${location.latitude}, ${location.longitude}")
                    }
                }
            }
            Log.d(TAG, "LocationRequest i LocationCallback zainicjalizowane")
        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas inicjalizacji LocationRequest: ${e.message}")
            locationRequest = null
            locationCallback = null
        }
    }

    // Uruchomienie nasłuchiwania na aktualizacje lokalizacji
    private fun startLocationUpdates() {
        // Sprawdź czy locationRequest i callback zostały zainicjalizowane
        if (locationRequest == null || locationCallback == null) {
            Log.d(TAG, "locationRequest lub locationCallback jest null, próba inicjalizacji")
            setupLocationUpdates()
            // Jeśli dalej są null, zakończ metodę
            if (locationRequest == null || locationCallback == null) {
                Log.e(TAG, "Nie można zainicjalizować locationRequest lub locationCallback")
                return
            }
        }

        // Sprawdź uprawnienia
        if (!hasLocationPermissions()) {
            Log.e(TAG, "Brak uprawnień do lokalizacji")
            return
        }

        try {
            // Jeszcze raz sprawdź uprawnienia tuż przed wywołaniem API
            if (hasLocationPermissions()) {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest!!,
                    locationCallback!!,
                    Looper.getMainLooper()
                )
                Log.d(TAG, "Uruchomiono aktualizacje lokalizacji")
            }
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException podczas żądania aktualizacji lokalizacji: ${se.message}")
            Toast.makeText(
                this,
                "Brak uprawnień do lokalizacji. Sprawdź ustawienia aplikacji.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas żądania aktualizacji lokalizacji: ${e.message}")
        }
    }

    // Zatrzymanie nasłuchiwania aktualizacji lokalizacji
    private fun stopLocationUpdates() {
        locationCallback?.let {
            try {
                fusedLocationClient.removeLocationUpdates(it)
                Log.d(TAG, "Zatrzymano aktualizacje lokalizacji")
            } catch (e: Exception) {
                Log.e(TAG, "Błąd podczas zatrzymywania aktualizacji lokalizacji: ${e.message}")
            }
        }
    }

    // Konfiguracja TelephonyCallback
    private fun setupTelephonyCallback() {
        if (!hasPhoneStatePermission()) {
            Log.e(TAG, "Brak uprawnień do konfiguracji listenera sieci")
            return
        }

        try {
            val callback = object : TelephonyCallback(), TelephonyCallback.CellInfoListener {
                override fun onCellInfoChanged(cellInfoList: MutableList<CellInfo>) {
                    processCellInfo(cellInfoList)
                }
            }

            telephonyCallback = callback
            telephonyManager.registerTelephonyCallback(mainExecutor, callback)
            Log.d(TAG, "Zarejestrowano TelephonyCallback")
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException podczas rejestracji TelephonyCallback: ${se.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas rejestracji TelephonyCallback: ${e.message}")
        }
    }

    // Przetwarzanie informacji o komórce
    private fun processCellInfo(cellInfoList: List<CellInfo>) {
        if (cellInfoList.isEmpty()) {
            Log.d(TAG, "Lista cellInfo jest pusta")
            return
        }

        // Usuń wszystkie poprzednie markery stacji bazowych
        runOnUiThread {
            for (marker in cellTowerMarkers) {
                mapView.overlays.remove(marker)
            }
            cellTowerMarkers.clear()
        }

        for (cellInfo in cellInfoList) {
            try {
                when (cellInfo) {
                    is CellInfoLte -> {
                        processCellInfoLte(cellInfo)
                        break // Po przetworzeniu pierwszej komórki przerwij
                    }
                    is CellInfoNr -> {
                        processCellInfoNr(cellInfo)
                        break
                    }
                    else -> {
                        Log.d(TAG, "Nieobsługiwany typ komórki: ${cellInfo.javaClass.simpleName}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Błąd podczas przetwarzania informacji o komórce: ${e.message}")
            }
        }

        // Odśwież mapę
        runOnUiThread {
            mapView.invalidate()
        }
    }

    // Przetwarzanie informacji o komórce LTE
    private fun processCellInfoLte(cellInfo: CellInfoLte) {
        val cellIdentity = cellInfo.cellIdentity
        val signalStrength = cellInfo.cellSignalStrength

        val rsrp = signalStrength.rsrp
        val rsrq = signalStrength.rsrq
        val rssnr = signalStrength.rssnr
        val cellId = cellIdentity.ci
        val tac = cellIdentity.tac
        val pci = cellIdentity.pci
        val earfcn = cellIdentity.earfcn

        runOnUiThread {
            tvRSRP.text = "RSRP: $rsrp dBm"
            tvRSRQ.text = "RSRQ: $rsrq dB"
            tvSINR.text = "SINR: $rssnr dB"
            tvCellId.text = "Cell ID: $cellId, TAC: $tac, PCI: $pci"
        }

        Log.d(TAG, "LTE - RSRP: $rsrp dBm, RSRQ: $rsrq dB, SINR: $rssnr dB, Cell ID: $cellId")

        // Dodaj marker nadajnika na mapie
        addCellTowerMarker(cellId, "LTE Cell: $cellId\nRSRP: $rsrp dBm\nPCI: $pci")
    }

    // Przetwarzanie informacji o komórce 5G (NR)
    private fun processCellInfoNr(cellInfo: CellInfoNr) {
        val cellIdentity = cellInfo.cellIdentity as CellIdentityNr
        val signalStrength = cellInfo.cellSignalStrength as CellSignalStrengthNr

        val ssRsrp = signalStrength.csiRsrp
        val ssRsrq = signalStrength.csiRsrq
        val ssSinr = signalStrength.csiSinr
        val nrArfcn = cellIdentity.nrarfcn
        val pci = cellIdentity.pci

        runOnUiThread {
            tvRSRP.text = "5G RSRP: $ssRsrp dBm"
            tvRSRQ.text = "5G RSRQ: $ssRsrq dB"
            tvSINR.text = "5G SINR: $ssSinr dB"
            tvCellId.text = "5G PCI: $pci, ARFCN: $nrArfcn"
        }

        Log.d(TAG, "5G - RSRP: $ssRsrp dBm, RSRQ: $ssRsrq dB, SINR: $ssSinr dB, PCI: $pci")

        // Dodaj marker nadajnika 5G
        addCellTowerMarker(pci, "5G Cell: $pci\nRSRP: $ssRsrp dBm\nARFCN: $nrArfcn")
    }

    private fun getCellularInfo() {
        if (!hasPhoneStatePermission()) {
            Log.e(TAG, "Brak uprawnień do pobierania informacji o sieci")
            return
        }

        try {
            if (hasPhoneStatePermission()) {
                telephonyManager.requestCellInfoUpdate(mainExecutor, object : TelephonyManager.CellInfoCallback() {
                    override fun onCellInfo(cellInfoList: List<CellInfo>) {
                        processCellInfo(cellInfoList)
                    }
                })
            }
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException podczas pobierania informacji o komórce: ${se.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas pobierania informacji o komórce: ${e.message}")
        }
    }

    private fun getUserLocation() {
        if (!hasLocationPermissions()) {
            Log.e(TAG, "Brak uprawnień do lokalizacji")
            return
        }

        try {
            if (hasLocationPermissions()) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        val currentLocation = GeoPoint(it.latitude, it.longitude)
                        updateUserMarker(currentLocation)
                        mapView.controller.setCenter(currentLocation)
                        mapView.controller.setZoom(15.0)
                        mapView.invalidate()
                        Log.d(TAG, "Lokalizacja zaktualizowana: ${it.latitude}, ${it.longitude}")
                        getCellularInfo() // Aktualizacja informacji o komórce po uzyskaniu lokalizacji
                    } ?: run {
                        Log.e(TAG, "Nie udało się pobrać lokalizacji")
                        // Jeśli nie można pobrać ostatniej lokalizacji, rozpocznij aktualizacje
                        if (hasLocationPermissions()) {
                            startLocationUpdates()
                        }
                    }
                }.addOnFailureListener { exception ->
                    Log.e(TAG, "Błąd podczas pobierania lokalizacji: ${exception.message}")
                    if (hasLocationPermissions()) {
                        startLocationUpdates() // Spróbuj rozpocząć aktualizacje w przypadku awarii
                    }
                }
            }
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException podczas pobierania lokalizacji: ${se.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Wyjątek podczas pobierania lokalizacji: ${e.message}")
        }
    }

    private fun updateUserMarker(location: GeoPoint) {
        try {
            runOnUiThread {
                // Jeśli marker już istnieje, aktualizuj jego pozycję
                if (userMarker != null) {
                    userMarker?.position = location
                } else {
                    // Jeśli marker nie istnieje, utwórz nowy
                    userMarker = Marker(mapView).apply {
                        position = location
                        title = "Twoja lokalizacja"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = resources.getDrawable(android.R.drawable.ic_menu_mylocation, theme)
                    }
                    mapView.overlays.add(userMarker)
                }
                mapView.invalidate() // Odśwież mapę
            }
        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas aktualizacji markera użytkownika: ${e.message}")
        }
    }

    // Dodawanie markera stacji bazowej na mapie
    private fun addCellTowerMarker(cellId: Int, title: String) {
        try {
            // Użyjemy przybliżonej lokalizacji - 500m od użytkownika w kierunku zależnym od cellId
            userMarker?.let { marker ->
                val userLocation = marker.position
                val angle = Math.toRadians((cellId % 360).toDouble())
                val distance = 500.0 // 500 metrów

                // Przybliżone obliczenie nowej lokalizacji
                val earthRadius = 6371000.0 // Promień Ziemi w metrach
                val lat2 = Math.asin(
                    Math.sin(Math.toRadians(userLocation.latitude)) * Math.cos(distance / earthRadius) +
                            Math.cos(Math.toRadians(userLocation.latitude)) * Math.sin(distance / earthRadius) * Math.cos(angle)
                )
                val lng2 = Math.toRadians(userLocation.longitude) +
                        Math.atan2(
                            Math.sin(angle) * Math.sin(distance / earthRadius) * Math.cos(Math.toRadians(userLocation.latitude)),
                            Math.cos(distance / earthRadius) - Math.sin(Math.toRadians(userLocation.latitude)) * Math.sin(lat2)
                        )

                val towerLocation = GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lng2))

                runOnUiThread {
                    val towerMarker = Marker(mapView).apply {
                        position = towerLocation
                        this.title = title
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = resources.getDrawable(android.R.drawable.ic_menu_compass, theme)
                    }

                    mapView.overlays.add(towerMarker)
                    cellTowerMarkers.add(towerMarker)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas tworzenia markera stacji bazowej: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()

        // Sprawdź uprawnienia przed wywołaniem funkcji wymagających uprawnień
        if (hasLocationPermissions()) {
            try {
                startLocationUpdates()
                getCellularInfo()
            } catch (se: SecurityException) {
                Log.e(TAG, "SecurityException w onResume: ${se.message}")
            }
        } else {
            Log.d(TAG, "Brak uprawnień lokalizacji w onResume")
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        stopLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Anulowanie rejestracji TelephonyCallback
        telephonyCallback?.let {
            try {
                telephonyManager.unregisterTelephonyCallback(it)
                Log.d(TAG, "Anulowano rejestrację TelephonyCallback")
            } catch (e: Exception) {
                Log.e(TAG, "Błąd przy anulowaniu rejestracji TelephonyCallback: ${e.message}")
            }
        }

        // Zatrzymaj aktualizacje lokalizacji
        stopLocationUpdates()
    }
}
