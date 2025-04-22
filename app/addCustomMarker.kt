
import android.graphics.Color
import android.widget.TextView

private fun addCustomMarker(tower: CellTower) {
    val position: LatLng = LatLng(tower.getLatitude(), tower.getLongitude())


    // Utwórz niestandardowy widok dla markera
    val textView: android.widget.TextView = android.widget.TextView(this)
    textView.setText(tower.getNetwork())
    textView.setBackgroundColor(android.graphics.Color.BLACK)
    textView.setTextColor(android.graphics.Color.YELLOW)
    textView.setPadding(10, 5, 10, 5)

    val marker: Marker = mMap.addMarker(
        AdvancedMarkerOptions()
            .position(position)
            .iconView(textView)
            .title(tower.getNetwork() + " - " + tower.getStandard() + " " + tower.getBand())
            .snippet("ID: " + tower.getId() + ", StationId: " + tower.getStationId())
    )

    marker.setTag(tower)
    allMarkers.add(marker)
}
