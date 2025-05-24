package com.example.cellsignalinfo // <<< ZMIEŃ NA SWOJĄ NAZWĘ PAKIETU

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.telephony.* // Nadal potrzebne, jeśli chcesz informacje o sieci, ale nie do lokalizacji BTS
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.twoja_nazwa_pakietu.RetrofitInstance
import com.google.android.gms.location.*
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

// Upewnij się, że te pliki (NadajnikInfo.kt, NadajnikiApiService.kt, RetrofitInstance.kt)
// istnieją w Twoim projekcie i są poprawnie skonfigurowane dla Twojego lokalnego API PHP.
// import com.example.twoja_nazwa_pakietu.NadajnikInfo
// import com.example.twoja_nazwa_pakietu.NadajnikiApiService
// import com.example.twoja_nazwa_pakietu.RetrofitInstance

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private val databaseTowerMarkers = mutableListOf<Marker>() // Markery nadajników z Twojej bazy

    private var permissionsGranted = false

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 101
        // Uprawnienie READ_PHONE_STATE jest potrzebne do TelephonyManager.listen/TelephonyCallback
        // oraz do uzyskania informacji o sieci. Jeśli nie chcesz tych informacji, możesz je usunąć.
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.INTERNET
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(applicationContext, getPreferences(Context.MODE_PRIVATE))
        setContentView(R.layout.activity_main) // Upewnij się, że masz ten layout

        mapView = findViewById(R.id.map_view) // Upewnij się, że ID mapy jest poprawne
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupMap()

        if (checkPermissions()) {
            permissionsGranted = true
            Log.d(TAG, "Uprawnienia już przyznane przy starcie")
            initializeAfterPermissions()
        } else {
            Log.d(TAG, "Prośba o uprawnienia przy starcie")
            requestPermissions()
        }
    }

    private fun initializeAfterPermissions() {
        setupLocationUpdates()      // Lokalizacja użytkownika
        setupTelephonyInfoDisplay() // Opcjonalnie: wyświetlanie informacji o sieci
        getUserLocation()           // Ustawienie początkowej mapy na lokalizację użytkownika
        fetchAndDisplayDatabaseMarkers() // NAJWAŻNIEJSZE: Pobranie i wyświetlenie markerów z Twojej bazy
    }

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(12.0) // Zoom początkowy
        // Centrum Poznania jako domyślne, zostanie nadpisane przez lokalizację użytkownika
        mapView.controller.setCenter(GeoPoint(52.4064, 16.9252))
    }

    private fun setupLocationUpdates() {
        if (!permissionsGranted) return

        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView)
        myLocationOverlay?.enableMyLocation()
        myLocationOverlay?.enableFollowLocation() // Mapa podąża za użytkownikiem
        myLocationOverlay?.isDrawAccuracyEnabled = true
        mapView.overlays.add(myLocationOverlay)

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L) // Co 10 sekund
            .setMinUpdateIntervalMillis(5000L) // Minimalny interwał 5 sekund
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                // MyLocationNewOverlay sam obsługuje aktualizację pozycji użytkownika na mapie
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    private fun getUserLocation() {
        if (!permissionsGranted || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                Log.d(TAG, "Uzyskano ostatnią znaną lokalizację: Lat: ${it.latitude}, Lon: ${it.longitude}")
                val startPoint = GeoPoint(it.latitude, it.longitude)
                mapView.controller.animateTo(startPoint) // Płynne przejście do lokalizacji użytkownika
                mapView.controller.setZoom(15.0) // Ustawienie odpowiedniego zoomu
            } ?: run {
                Log.d(TAG, "Ostatnia znana lokalizacja jest null, czekam na aktualizacje.")
            }
        }
    }

    // Opcjonalna funkcja do wyświetlania informacji o sieci (jeśli chcesz)
    private fun setupTelephonyInfoDisplay() {
        if (!permissionsGranted || ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Brak uprawnienia READ_PHONE_STATE do odczytu informacji o sieci.")
            return
        }

        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        val phoneStateListener = object : PhoneStateListener() {
            // Możesz tu nasłuchiwać na zmiany np. onSignalStrengthsChanged, onServiceStateChanged
            // Poniżej prosty przykład pobrania danych raz.
        }
        // Jeśli chcesz nasłuchiwać na zmiany, musisz zarejestrować listenera:
        // telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or PhoneStateListener.LISTEN_SERVICE_STATE)

        // Jednorazowe pobranie informacji o sieci
        val networkOperatorName = telephonyManager.networkOperatorName
        val networkType = when (telephonyManager.dataNetworkType) { // lub .voiceNetworkType
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "3G (HSPAP)"
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G (UMTS)"
            TelephonyManager.NETWORK_TYPE_EDGE -> "2G (EDGE)"
            TelephonyManager.NETWORK_TYPE_GPRS -> "2G (GPRS)"
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            else -> "Inny (${telephonyManager.dataNetworkType})"
        }
        Log.i(TAG, "Operator sieci: $networkOperatorName, Typ sieci danych: $networkType")
        Toast.makeText(this, "Sieć: $networkOperatorName ($networkType)", Toast.LENGTH_LONG).show()
    }


    // --- Markery nadajników z Twojego lokalnego API PHP ---
    private fun fetchAndDisplayDatabaseMarkers() {
        if (!permissionsGranted) {
            Log.w(TAG, "Brak uprawnień, nie można pobrać markerów z bazy.")
            return
        }

        Log.d(TAG, "Rozpoczynanie pobierania danych nadajników z lokalnego API...")
        lifecycleScope.launch { // Użyj lifecycleScope do automatycznego zarządzania korutyną
            try {
                // Upewnij się, że RetrofitInstance i NadajnikiApiService są poprawnie skonfigurowane
                // dla Twojego lokalnego API PHP.
                val response = RetrofitInstance.api.getNadajniki()
                if (response.isSuccessful) {
                    val nadajnikiList = response.body()
                    if (nadajnikiList != null && nadajnikiList.isNotEmpty()) {
                        Log.d(TAG, "Otrzymano ${nadajnikiList.size} nadajników z lokalnego API.")
                        displayMarkersFromLocalDatabase(nadajnikiList)
                    } else {
                        Log.d(TAG, "Lokalne API zwróciło pustą listę lub null.")
                        if (nadajnikiList?.isEmpty() == true) {
                            Toast.makeText(this@MainActivity, "Brak danych nadajników z serwera.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.e(TAG, "Błąd lokalnego API: ${response.code()} - ${response.errorBody()?.string()}")
                    Toast.makeText(this@MainActivity, "Błąd serwera nadajników: ${response.code()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Wyjątek podczas pobierania danych z lokalnego API: ${e.message}", e)
                Toast.makeText(this@MainActivity, "Błąd połączenia z serwerem nadajników: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun displayMarkersFromLocalDatabase(nadajnikiList: List<NadajnikInfo>) {
        // Upewnij się, że klasa NadajnikInfo jest zdefiniowana i pasuje do odpowiedzi JSON
        // z Twojego API PHP (pola: siec_id, miejscowosc, lokalizacja, LATIuke, LONGuke).

        if (!::mapView.isInitialized) {
            Log.e(TAG, "MapView nie zostało zainicjalizowane. Nie można dodać markerów z bazy.")
            return
        }

        runOnUiThread { // Operacje na UI muszą być wykonane w głównym wątku
            // Usuń poprzednie markery z bazy, aby uniknąć duplikatów przy odświeżaniu
            for (marker in databaseTowerMarkers) {
                mapView.overlays.remove(marker)
            }
            databaseTowerMarkers.clear()

            var count = 0
            for (nadajnik in nadajnikiList) {
                // Upewnij się, że współrzędne (LATIuke, LONGuke w Twojej bazie) nie są null
                if (nadajnik.latitude != null && nadajnik.longitude != null) {
                    val towerGeoPoint = GeoPoint(nadajnik.latitude, nadajnik.longitude)
                    // Tytuł i opis markera - dostosuj wg potrzeb
                    val markerTitle = "${nadajnik.siecId ?: "Brak sieci"} - ${nadajnik.lokalizacja ?: nadajnik.miejscowosc ?: "Brak lokalizacji"}"
                    val markerSnippet = "Standard: (tu wstaw dane o standardzie jeśli masz w API), Miejscowość: ${nadajnik.miejscowosc ?: "N/A"}"

                    val dbMarker = Marker(mapView).apply {
                        position = towerGeoPoint
                        this.title = markerTitle
                        this.snippet = markerSnippet
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        // Użyj innej ikony dla markerów z bazy, aby je odróżnić od lokalizacji użytkownika
                        // Możesz stworzyć własną ikonę w res/drawable
                        // icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.your_database_marker_icon)
                        icon = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.ic_menu_myplaces) // Przykładowa ikona
                    }
                    mapView.overlays.add(dbMarker)
                    databaseTowerMarkers.add(dbMarker)
                    count++
                } else {
                    Log.w(TAG, "Pominięto nadajnik: ${nadajnik.lokalizacja} (ID sieci: ${nadajnik.siecId}) z powodu braku współrzędnych.")
                }
            }
            mapView.invalidate() // Odśwież mapę, aby pokazać nowe markery
            Log.d(TAG, "Zakończono dodawanie $count markerów z lokalnej bazy danych.")
            if (count > 0) {
                Toast.makeText(this, "Załadowano $count nadajników z bazy", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- Zarządzanie uprawnieniami ---
    private fun checkPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
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
            permissionsGranted = grantResults.isNotEmpty() &&
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (permissionsGranted) {
                Log.d(TAG, "Wszystkie uprawnienia zostały przyznane po prośbie")
                initializeAfterPermissions()
            } else {
                Log.e(TAG, "Nie wszystkie uprawnienia zostały przyznane")
                val missingPermissions = permissions.filterIndexed { index, _ ->
                    grantResults.getOrNull(index) != PackageManager.PERMISSION_GRANTED
                }
                Log.e(TAG, "Brakujące uprawnienia: $missingPermissions")
                showPermissionRationale()
            }
        }
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("Wymagane uprawnienia")
            .setMessage("Aplikacja wymaga uprawnień do lokalizacji i dostępu do internetu, aby poprawnie działać. Uprawnienie do stanu telefonu jest opcjonalne dla wyświetlania informacji o sieci.")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume() // Ważne dla cyklu życia mapy OSMDroid
        if (permissionsGranted) {
            // Wznów aktualizacje lokalizacji, jeśli były zatrzymane w onPause
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                if(::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
                    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L).build()
                    fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
                }
            }
            myLocationOverlay?.enableMyLocation()
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause() // Ważne dla cyklu życia mapy OSMDroid
        // Zatrzymaj aktualizacje lokalizacji, aby oszczędzać baterię
        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        myLocationOverlay?.disableMyLocation()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDetach() // Zwolnij zasoby mapy OSMDroid
    }
}
