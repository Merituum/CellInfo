
import CsvDataLoader.loadCellTowersFromCsv
import android.R
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.CellInfo

class MapsActivity : FragmentActivity(), OnMapReadyCallback {
    private var mMap: GoogleMap? = null
    private var cellTowers: List<CellTower>? = null
    private var cellTowerDetector: CellTowerDetector? = null
    private var currentConnectedMarker: Marker? = null
    private val allMarkers: MutableList<Marker> = ArrayList<Marker>()

    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)


        // Uzyskaj SupportMapFragment i powiadom, gdy mapa jest gotowa do użycia
        val mapFragment: SupportMapFragment = getSupportFragmentManager()
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)


        // Inicjalizacja detektora nadajników
        cellTowerDetector = CellTowerDetector(this)


        // Wczytaj dane nadajników
        cellTowers = loadCellTowersFromCsv(this, "nadajniki_poznan.csv")


        // Sprawdź i poproś o uprawnienia
        checkPermissions()
    }

    override fun onMapReady(googleMap: GoogleMap?) {
        mMap = googleMap


        // Dodaj markery dla wszystkich nadajników
        addCellTowerMarkers()


        // Ustaw słuchacza kliknięć markerów
        mMap.setOnMarkerClickListener { marker ->
            // Pokaż informacje o nadajniku
            marker.showInfoWindow()
            true
        }


        // Ustaw początkowe położenie mapy na Poznań
        val poznan: LatLng = LatLng(52.4064, 16.9252)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(poznan, 12))


        // Rozpocznij cykliczne sprawdzanie połączonego nadajnika
        startCellTowerMonitoring()
    }

    private fun addCellTowerMarkers() {
        for (tower in cellTowers!!) {
            val position: LatLng = LatLng(tower.getLatitude(), tower.getLongitude())

            val markerOptions: MarkerOptions = MarkerOptions()
                .position(position)
                .title(tower.getNetwork() + " - " + tower.getStandard() + " " + tower.getBand())
                .snippet("ID: " + tower.getId() + ", StationId: " + tower.getStationId())

            val marker: Marker = mMap.addMarker(markerOptions)
            marker.setTag(tower)
            allMarkers.add(marker)
        }
    }

    private fun startCellTowerMonitoring() {
        val handler: Handler = Handler()
        handler.postDelayed(object : Runnable {
            override fun run() {
                updateConnectedCellTower()
                handler.postDelayed(this, 5000) // Sprawdzaj co 5 sekund
            }
        }, 1000)
    }

    private fun updateConnectedCellTower() {
        val cellInfo = cellTowerDetector!!.currentCellInfo
        if (cellInfo != null) {
            // Resetuj poprzedni marker
            if (currentConnectedMarker != null) {
                currentConnectedMarker.setIcon(
                    BitmapDescriptorFactory.defaultMarker(
                        BitmapDescriptorFactory.HUE_RED
                    )
                )
            }


            // Znajdź najbliższy nadajnik do aktualnej pozycji
            // W rzeczywistości powinieneś dopasować na podstawie identyfikatorów sieci
            val connectedTower = findConnectedTower(cellInfo)
            if (connectedTower != null) {
                for (marker in allMarkers) {
                    val tower = marker.getTag() as CellTower
                    if (tower.getId() === connectedTower.getId()) {
                        // Zaznacz połączony nadajnik
                        marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                        currentConnectedMarker = marker


                        // Aktualizuj informacje o sygnale
                        val signalStrength = cellTowerDetector!!.signalStrength
                        marker.setSnippet("Siła sygnału: $signalStrength dBm")
                        if (marker.isInfoWindowShown()) {
                            marker.hideInfoWindow()
                            marker.showInfoWindow()
                        }
                        break
                    }
                }
            }
        }
    }

    private fun findConnectedTower(cellInfo: CellInfo): CellTower {
        // Implementacja logiki dopasowania informacji o komórce do danych z CSV
        // To jest uproszczona wersja - w rzeczywistości musisz dopasować na podstawie
        // identyfikatorów sieci, CID, LAC itp.
        return cellTowers!![0] // Przykład - zwróć pierwszy nadajnik
    }

    private fun checkPermissions() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) !== PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) !== PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf<String>(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.READ_PHONE_STATE
                ),
                1
            )
        }
    }
}