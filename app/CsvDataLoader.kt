
import android.content.Context
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.ArrayList

object CsvDataLoader {
    fun loadCellTowersFromCsv(context: android.content.Context, fileName: String): List<CellTower> {
        val cellTowers: MutableList<CellTower> = java.util.ArrayList<CellTower>()

        try {
            val `is`: java.io.InputStream = context.getAssets().open(fileName)
            val reader: java.io.BufferedReader =
                java.io.BufferedReader(java.io.InputStreamReader(`is`))
            var line: String


            // Pomiń nagłówek
            reader.readLine()

            while ((reader.readLine().also { line = it }) != null) {
                val data: Array<String> =
                    line.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (data.size >= 30) {
                    val tower: CellTower = CellTower()
                    tower.setId(data.get(0).toInt())
                    tower.setNetwork(data.get(1))
                    tower.setLocation(data.get(4))
                    tower.setStandard(data.get(5))
                    tower.setBand(data.get(6))
                    tower.setLongitude(data.get(24).toDouble())
                    tower.setLatitude(data.get(25).toDouble())
                    tower.setStationId(data.get(27))

                    cellTowers.add(tower)
                }
            }
            reader.close()
        } catch (e: java.io.IOException) {
            e.printStackTrace()
        }

        return cellTowers
    }
}