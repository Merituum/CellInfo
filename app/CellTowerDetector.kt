import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.TelephonyManager

class CellTowerDetector(context: android.content.Context) {
    private val context: android.content.Context

    init {
        this.context = context
    }

    @get:android.annotation.SuppressLint("MissingPermission")
    val currentCellInfo: CellInfo?
        get() {
            val telephonyManager: TelephonyManager =
                context.getSystemService(android.content.Context.TELEPHONY_SERVICE) as TelephonyManager

            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) !== PackageManager.PERMISSION_GRANTED
            ) {
                return null
            }

            val cellInfoList: List<CellInfo>? = telephonyManager.getAllCellInfo()
            if (cellInfoList != null && !cellInfoList.isEmpty()) {
                for (cellInfo: CellInfo in cellInfoList) {
                    if (cellInfo.isRegistered()) {
                        return cellInfo
                    }
                }
            }
            return null
        }

    val signalStrength: Int
        get() {
            val cellInfo: CellInfo? = currentCellInfo
            if (cellInfo != null) {
                if (cellInfo is CellInfoLte) {
                    return (cellInfo as CellInfoLte).getCellSignalStrength().getDbm()
                } else if (cellInfo is CellInfoGsm) {
                    return (cellInfo as CellInfoGsm).getCellSignalStrength().getDbm()
                }
                // Dodaj obsługę innych typów sieci
            }
            return 0
        }
}