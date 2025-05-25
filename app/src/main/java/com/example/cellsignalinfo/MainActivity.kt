package com.example.cellsignalinfo
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.telephony.*
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.cellsignalinfo.RetrofitInstance
import com.google.android.gms.location.*
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private val databaseTowerMarkers = mutableListOf<Marker>()

    // TextViews for signal info
    private lateinit var tvRsrp: TextView
    private lateinit var tvRsrq: TextView
    private lateinit var tvSinr: TextView
    private lateinit var tvCellId: TextView

    private var telephonyManager: TelephonyManager? = null
    private var cellInfoCallback: Any? = null // For TelephonyCallback or PhoneStateListener

    private var permissionsGranted = false

    // NOWE FUNKCJE DLA KOLOROWYCH MARKERÓW
    data class StationGroup(
        val latitude: Double,
        val longitude: Double,
        val nadajniki: MutableList<NadajnikInfo> = mutableListOf()
    ) {
        fun addNadajnik(nadajnik: NadajnikInfo) {
            nadajniki.add(nadajnik)
        }

        fun getUniqueOperators(): List<String> {
            return nadajniki.mapNotNull { it.siecId }.distinct()
        }
    }

    private fun getOperatorColor(operator: String?): Int {
        return when (operator?.lowercase()) {
            "orange" -> Color.rgb(255, 165, 0) // Pomarańczowy
            "plus" -> Color.rgb(0, 128, 0) // Zielony
            "t-mobile", "tmobile" -> Color.rgb(255, 0, 255) // Magentowy
            "play" -> Color.rgb(128, 0, 128) // Fioletowy
            else -> Color.GRAY // Domyślny kolor dla nieznanych operatorów
        }
    }

    private fun createColoredMarkerDrawable(colors: List<Int>): Drawable {
        val size = 60 // Rozmiar markera w dp
        val sizePixels = (size * resources.displayMetrics.density).toInt()

        val bitmap = Bitmap.createBitmap(sizePixels, sizePixels, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        when (colors.size) {
            1 -> {
                // Jeden kolor - pełne koło
                val paint = Paint().apply {
                    color = colors[0]
                    isAntiAlias = true
                    style = Paint.Style.FILL
                }
                val radius = sizePixels / 2f
                canvas.drawCircle(radius, radius, radius * 0.2f, paint)

                // Biała obwódka
                val strokePaint = Paint().apply {
                    color = Color.WHITE
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeWidth = 4f
                }
                canvas.drawCircle(radius, radius, radius * 0.2f, strokePaint)
            }
            2 -> {
                // Dwa kolory - podzielone pionowo
                val paint1 = Paint().apply { color = colors[0]; isAntiAlias = true }
                val paint2 = Paint().apply { color = colors[1]; isAntiAlias = true }

                val centerX = sizePixels / 2f
                val centerY = sizePixels / 2f
                val radius = sizePixels / 2f * 0.2f

                val rect = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
                canvas.drawArc(rect, -90f, 180f, true, paint1)
                canvas.drawArc(rect, 90f, 180f, true, paint2)
            }
            3 -> {
                // Trzy kolory - podzielone na 3 sektory
                val centerX = sizePixels / 2f
                val centerY = sizePixels / 2f
                val radius = sizePixels / 2f * 0.2f

                val paint1 = Paint().apply { color = colors[0]; isAntiAlias = true }
                val paint2 = Paint().apply { color = colors[1]; isAntiAlias = true }
                val paint3 = Paint().apply { color = colors[2]; isAntiAlias = true }

                val rect = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
                canvas.drawArc(rect, -90f, 120f, true, paint1)
                canvas.drawArc(rect, 30f, 120f, true, paint2)
                canvas.drawArc(rect, 150f, 120f, true, paint3)
            }
            4 -> {
                // Cztery kolory - podzielone na ćwiartki
                val centerX = sizePixels / 2f
                val centerY = sizePixels / 2f
                val radius = sizePixels / 2f * 0.2f

                val rect = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

                val paints = colors.map { color ->
                    Paint().apply {
                        this.color = color
                        isAntiAlias = true
                    }
                }

                canvas.drawArc(rect, -90f, 90f, true, paints[0]) // Góra
                canvas.drawArc(rect, 0f, 90f, true, paints[1])   // Prawo
                canvas.drawArc(rect, 90f, 90f, true, paints[2])  // Dół
                canvas.drawArc(rect, 180f, 90f, true, paints[3]) // Lewo
            }
            else -> {
                // Więcej niż 4 kolory - użyj pierwszych 4
                return createColoredMarkerDrawable(colors.take(4))
            }
        }

        // Dodaj czarną obwódkę
        val strokePaint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val radius = sizePixels / 2f
        canvas.drawCircle(radius, radius, radius * 0.2f, strokePaint)

        return BitmapDrawable(resources, bitmap)
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 101
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
        setContentView(R.layout.activity_main) // Upewnij się, że ten layout istnieje i ma odpowiednie ID

        // Initialize TextViews from layout
        tvRsrp = findViewById(R.id.tv_rsrp)
        tvRsrq = findViewById(R.id.tv_rsrq)
        tvSinr = findViewById(R.id.tv_sinr)
        tvCellId = findViewById(R.id.tv_cell_id)

        mapView = findViewById(R.id.map_view)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?

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
        setupLocationUpdates()
        setupTelephonyInfoDisplay() // Tutaj odczytujemy dane sygnału
        getUserLocation()
        fetchAndDisplayDatabaseMarkers() // Markery z Twojego lokalnego API PHP
    }

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(12.0)
        mapView.controller.setCenter(GeoPoint(52.4064, 16.9252)) // Poznań
    }

    private fun setupLocationUpdates() {
        if (!permissionsGranted || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView)
        myLocationOverlay?.enableMyLocation()
        myLocationOverlay?.enableFollowLocation()
        myLocationOverlay?.isDrawAccuracyEnabled = true
        mapView.overlays.add(myLocationOverlay)

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L).build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) { /* Handled by MyLocationNewOverlay */ }
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun getUserLocation() {
        if (!permissionsGranted || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                Log.d(TAG, "Uzyskano ostatnią znaną lokalizację: Lat: ${it.latitude}, Lon: ${it.longitude}")
                val startPoint = GeoPoint(it.latitude, it.longitude)
                mapView.controller.animateTo(startPoint)
                mapView.controller.setZoom(15.0)
            }
        }
    }

    private fun setupTelephonyInfoDisplay() {
        if (!permissionsGranted) {
            Log.w(TAG, "Brak uprawnień do odczytu informacji o sieci.")
            updateSignalInfoUI("Brak uprawnień", "N/A", "N/A", "N/A", "N/A", "N/A")
            return
        }

        if (telephonyManager == null) {
            Log.e(TAG, "TelephonyManager jest null.")
            updateSignalInfoUI("Błąd TM", "N/A", "N/A", "N/A", "N/A", "N/A")
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Brak uprawnienia ACCESS_FINE_LOCATION, odczyt CellInfo może być ograniczony.")
            updateSignalInfoUI(telephonyManager?.networkOperatorName ?: "N/A", "Brak upr. Loc.", "N/A", "N/A", "N/A", "N/A")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31 for full TelephonyCallback features
            registerTelephonyCallbackS()
        } else {
            registerPhoneStateListenerLegacy()
        }
        requestCellInfoUpdate() // Initial fetch
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerTelephonyCallbackS() {
        val callback = @RequiresApi(Build.VERSION_CODES.S) object : TelephonyCallback(),
            TelephonyCallback.CellInfoListener,
            TelephonyCallback.SignalStrengthsListener {

            override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>) {
                Log.d(TAG, "onCellInfoChanged (API 31+): ${cellInfo.size} cells")
                processCellInfo(cellInfo)
            }

            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                Log.d(TAG, "onSignalStrengthsChanged (API 31+)")
                requestCellInfoUpdate() // Refresh CellInfo as signal strength might affect which cell is primary
            }
        }
        telephonyManager?.registerTelephonyCallback(mainExecutor, callback)
        cellInfoCallback = callback
        Log.d(TAG, "Zarejestrowano TelephonyCallback (API 31+)")
    }

    @Suppress("DEPRECATION")
    private fun registerPhoneStateListenerLegacy() {
        val phoneStateListener = object : PhoneStateListener() {
            @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE])
            override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>?) {
                super.onCellInfoChanged(cellInfo)
                Log.d(TAG, "onCellInfoChanged (starsze API): ${cellInfo?.size ?: 0} cells")
                processCellInfo(cellInfo)
            }

            override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
                super.onSignalStrengthsChanged(signalStrength)
                Log.d(TAG, "onSignalStrengthsChanged (starsze API)")
                requestCellInfoUpdate()
            }
        }
        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CELL_INFO or PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        cellInfoCallback = phoneStateListener
        Log.d(TAG, "Zarejestrowano PhoneStateListener (starsze API)")
    }

    private fun requestCellInfoUpdate() {
        if (telephonyManager == null || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Nie można zażądać aktualizacji CellInfo - brak TM lub uprawnień lokalizacji.")
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
                updateSignalInfoUI(telephonyManager?.networkOperatorName ?: "N/A", "Brak upr. Loc.", "N/A", "N/A", "N/A", "N/A")
            }
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // API 29
            telephonyManager?.requestCellInfoUpdate(mainExecutor, object : TelephonyManager.CellInfoCallback() {
                override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                    Log.d(TAG, "requestCellInfoUpdate (API 29+) onCellInfo: ${cellInfo.size} cells")
                    processCellInfo(cellInfo)
                }
                override fun onError(errorCode: Int, detail: Throwable?) { // Dodano obsługę błędu
                    super.onError(errorCode, detail)
                    Log.e(TAG, "requestCellInfoUpdate onError: $errorCode, ${detail?.message}")
                    updateSignalInfoUI(telephonyManager?.networkOperatorName ?: "N/A", "Błąd odczytu CellInfo", "N/A", "N/A", "N/A", "N/A")
                }
            })
            Log.d(TAG, "Zażądano requestCellInfoUpdate (API 29+)")
        } else {
            @Suppress("DEPRECATION")
            val currentCellInfo = telephonyManager?.allCellInfo
            Log.d(TAG, "getAllCellInfo (starsze API) pobrano: ${currentCellInfo?.size ?: 0} cells")
            processCellInfo(currentCellInfo)
        }
    }

    private fun processCellInfo(cellInfoList: List<CellInfo>?) {
        val currentOperatorName = telephonyManager?.networkOperatorName ?: "N/A"
        var rsrpVal = "N/A"
        var rsrqVal = "N/A"
        var sinrVal = "N/A"
        var cellIdString = "N/A"
        var currentNetworkTypeString = "N/A"

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "processCellInfo: Brak uprawnienia ACCESS_FINE_LOCATION.")
            updateSignalInfoUI(currentOperatorName, "Brak upr. Loc.", "N/A", "N/A", "N/A", "N/A")
            return
        }

        if (cellInfoList.isNullOrEmpty()) {
            Log.w(TAG, "processCellInfo: Lista CellInfo jest pusta lub null.")
            currentNetworkTypeString = getNetworkTypeString(telephonyManager?.dataNetworkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN)
            updateSignalInfoUI(currentOperatorName, currentNetworkTypeString, rsrpVal, rsrqVal, sinrVal, cellIdString)
            return
        }

        var foundRegisteredCell = false
        for (cellInfo in cellInfoList) {
            if (cellInfo.isRegistered) {
                foundRegisteredCell = true
                when (cellInfo) {
                    is CellInfoLte -> {
                        currentNetworkTypeString = "LTE"
                        val csl = cellInfo.cellSignalStrength
                        val cil = cellInfo.cellIdentity
                        rsrpVal = if (csl.rsrp == CellInfo.UNAVAILABLE) "N/A" else "${csl.rsrp} dBm"
                        rsrqVal = if (csl.rsrq == CellInfo.UNAVAILABLE) "N/A" else "${csl.rsrq} dB"
                        sinrVal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && csl.rssnr != CellInfo.UNAVAILABLE) "${csl.rssnr} dB" else "N/A"
                        cellIdString = if (cil.ci == CellInfo.UNAVAILABLE || cil.ci == Integer.MAX_VALUE) "N/A" else "CI:${cil.ci} TAC:${cil.tac} PCI:${cil.pci}"
                    }
                    is CellInfoWcdma -> {
                        currentNetworkTypeString = "WCDMA/UMTS"
                        val csw = cellInfo.cellSignalStrength
                        val ciw = cellInfo.cellIdentity
                        rsrpVal = if (csw.dbm == CellInfo.UNAVAILABLE) "N/A" else "${csw.dbm} dBm (RSSI)"
                        cellIdString = if (ciw.cid == CellInfo.UNAVAILABLE || ciw.cid == Integer.MAX_VALUE) "N/A" else "CID:${ciw.cid} LAC:${ciw.lac} PSC:${ciw.psc}"
                    }
                    is CellInfoGsm -> {
                        currentNetworkTypeString = "GSM"
                        val csg = cellInfo.cellSignalStrength
                        val cig = cellInfo.cellIdentity
                        rsrpVal = if (csg.dbm == CellInfo.UNAVAILABLE) "N/A" else "${csg.dbm} dBm (RSSI)"
                        cellIdString = if (cig.cid == CellInfo.UNAVAILABLE || cig.cid == Integer.MAX_VALUE) "N/A" else "CID:${cig.cid} LAC:${cig.lac}"
                    }
                    is CellInfoNr -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        currentNetworkTypeString = "5G NR"
                        val csnr = cellInfo.cellSignalStrength as CellSignalStrengthNr
                        val cinr = cellInfo.cellIdentity as CellIdentityNr
                        rsrpVal = if (csnr.ssRsrp == CellInfo.UNAVAILABLE) "N/A" else "${csnr.ssRsrp} dBm (SSRSRP)"
                        rsrqVal = if (csnr.ssRsrq == CellInfo.UNAVAILABLE) "N/A" else "${csnr.ssRsrq} dB (SSRSRQ)"
                        sinrVal = if (csnr.ssSinr == CellInfo.UNAVAILABLE) "N/A" else "${csnr.ssSinr} dB (SSSINR)"
                        cellIdString = if (cinr.nci == Long.MAX_VALUE || cinr.nci == CellInfo.UNAVAILABLE_LONG) "N/A" else "NCI:${cinr.nci} TAC:${cinr.tac} PCI:${cinr.pci}"
                    }
                    else -> {
                        currentNetworkTypeString = "Inny (${cellInfo.javaClass.simpleName})"
                        Log.d(TAG, "Nieobsługiwany zarejestrowany typ CellInfo: ${cellInfo.javaClass.simpleName}")
                    }
                }
                Log.d(TAG, "$currentNetworkTypeString: RSRP/RSSI=$rsrpVal, RSRQ=$rsrqVal, SINR/RSSNR=$sinrVal, CellID=$cellIdString")
                break // Processed registered cell
            }
        }

        if (!foundRegisteredCell) {
            Log.w(TAG, "Nie znaleziono zarejestrowanej komórki. Wyświetlam ogólny typ sieci.")
            currentNetworkTypeString = getNetworkTypeString(telephonyManager?.dataNetworkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN)
        }

        updateSignalInfoUI(currentOperatorName, currentNetworkTypeString, rsrpVal, rsrqVal, sinrVal, cellIdString)
    }

    private fun updateSignalInfoUI(operator: String, networkType: String, rsrp: String, rsrq: String, sinr: String, cellId: String) {
        runOnUiThread {
            title = "$operator ($networkType)" // Set activity title
            tvRsrp.text = "RSRP: $rsrp"
            tvRsrq.text = "RSRQ: $rsrq"
            tvSinr.text = "SINR: $sinr"
            tvCellId.text = "Cell ID: $cellId"
        }
    }

    private fun getNetworkTypeString(networkTypeConst: Int): String {
        return when (networkTypeConst) {
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "3G (HSPAP)"
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G (UMTS)"
            TelephonyManager.NETWORK_TYPE_EDGE -> "2G (EDGE)"
            TelephonyManager.NETWORK_TYPE_GPRS -> "2G (GPRS)"
            TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
            TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
            TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN"
            TelephonyManager.NETWORK_TYPE_UNKNOWN -> "Nieznany"
            else -> "Inny ($networkTypeConst)"
        }
    }

    // --- Markery nadajników z Twojego lokalnego API PHP ---
    private fun fetchAndDisplayDatabaseMarkers() {
        if (!permissionsGranted) {
            Log.w(TAG, "Brak uprawnień, nie można pobrać markerów z bazy.")
            return
        }
        Log.d(TAG, "Rozpoczynanie pobierania danych nadajników z lokalnego API...")
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.getNadajniki() // Zakładam, że RetrofitInstance jest poprawnie skonfigurowany
                if (response.isSuccessful) {
                    val nadajnikiList = response.body()
                    if (nadajnikiList != null && nadajnikiList.isNotEmpty()) {
                        Log.d(TAG, "Otrzymano ${nadajnikiList.size} nadajników z lokalnego API.")
                        displayMarkersFromLocalDatabase(nadajnikiList)
                    } else {
                        Log.d(TAG, "Lokalne API zwróciło pustą listę lub null.")
                        if (nadajnikiList?.isEmpty() == true) Toast.makeText(this@MainActivity, "Brak danych nadajników z serwera.", Toast.LENGTH_SHORT).show()
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

    // ZMIENIONA FUNKCJA - TERAZ Z KOLOROWYMI MARKERAMI
    private fun displayMarkersFromLocalDatabase(nadajnikiList: List<NadajnikInfo>) {
        if (!::mapView.isInitialized) {
            Log.e(TAG, "MapView nie zostało zainicjalizowane.")
            return
        }

        runOnUiThread {
            // Usuń poprzednie markery
            for (marker in databaseTowerMarkers) mapView.overlays.remove(marker)
            databaseTowerMarkers.clear()

            // Grupuj nadajniki według lokalizacji (z tolerancją na niewielkie różnice w koordynatach)
            val stationGroups = mutableMapOf<String, StationGroup>()

            for (nadajnik in nadajnikiList) {
                if (nadajnik.latitude != null && nadajnik.longitude != null) {
                    // Zaokrąglij koordynaty do 4 miejsc po przecinku dla grupowania
                    val roundedLat = String.format("%.4f", nadajnik.latitude)
                    val roundedLon = String.format("%.4f", nadajnik.longitude)
                    val locationKey = "$roundedLat,$roundedLon"

                    val group = stationGroups.getOrPut(locationKey) {
                        StationGroup(nadajnik.latitude, nadajnik.longitude)
                    }
                    group.addNadajnik(nadajnik)
                } else {
                    Log.w(TAG, "Pominięto nadajnik (brak koordynatów): ${nadajnik.lokalizacja}")
                }
            }

            // Twórz markery dla każdej grupy
            var markerCount = 0
            for (group in stationGroups.values) {
                val uniqueOperators = group.getUniqueOperators()
                val operatorColors = uniqueOperators.map { getOperatorColor(it) }

                val towerGeoPoint = GeoPoint(group.latitude, group.longitude)

                // Twórz tytuł i opis markera
                val operatorNames = uniqueOperators.joinToString(", ")
                val markerTitle = if (uniqueOperators.size == 1) {
                    "${uniqueOperators.first()} - ${group.nadajniki.first().lokalizacja ?: group.nadajniki.first().miejscowosc ?: ""}"
                } else {
                    "Stacja wielooperatorska - ${group.nadajniki.first().lokalizacja ?: group.nadajniki.first().miejscowosc ?: ""}"
                }

                val markerSnippet = buildString {
                    append("Operatorzy: $operatorNames\n")
                    append("Miejscowość: ${group.nadajniki.first().miejscowosc ?: "N/A"}\n")
                    append("Liczba nadajników: ${group.nadajniki.size}")
                }

                // Twórz marker z kolorową ikoną
                val dbMarker = Marker(mapView).apply {
                    position = towerGeoPoint
                    title = markerTitle
                    snippet = markerSnippet
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                    // Ustaw kolorową ikonę
                    icon = if (operatorColors.isNotEmpty()) {
                        createColoredMarkerDrawable(operatorColors)
                    } else {
                        ContextCompat.getDrawable(this@MainActivity, android.R.drawable.ic_menu_myplaces)
                    }
                }

                mapView.overlays.add(dbMarker)
                databaseTowerMarkers.add(dbMarker)
                markerCount++
            }

            mapView.invalidate()
            Log.d(TAG, "Zakończono dodawanie $markerCount markerów z lokalnej bazy danych.")
            if (markerCount > 0) Toast.makeText(this, "Załadowano $markerCount stacji z bazy", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Zarządzanie uprawnieniami ---
    private fun checkPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            permissionsGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (permissionsGranted) {
                Log.d(TAG, "Wszystkie uprawnienia zostały przyznane po prośbie")
                initializeAfterPermissions()
            } else {
                Log.e(TAG, "Nie wszystkie uprawnienia zostały przyznane")
                showPermissionRationale()
            }
        }
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("Wymagane uprawnienia")
            .setMessage("Aplikacja wymaga uprawnień do lokalizacji i stanu telefonu, aby poprawnie działać.")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .create().show()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        if (permissionsGranted && ::fusedLocationClient.isInitialized && ::locationCallback.isInitialized &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L).build()
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
        myLocationOverlay?.enableMyLocation()
        requestCellInfoUpdate() // Odśwież dane sygnału po wznowieniu
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        myLocationOverlay?.disableMyLocation()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (telephonyManager != null && cellInfoCallback != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && cellInfoCallback is TelephonyCallback) {
                telephonyManager?.unregisterTelephonyCallback(cellInfoCallback as TelephonyCallback)
                Log.d(TAG, "Odrejestrowano TelephonyCallback")
            } else if (cellInfoCallback is PhoneStateListener) {
                @Suppress("DEPRECATION")
                telephonyManager?.listen(cellInfoCallback as PhoneStateListener, PhoneStateListener.LISTEN_NONE)
                Log.d(TAG, "Odrejestrowano PhoneStateListener")
            }
        }
        mapView.onDetach()
    }
}
