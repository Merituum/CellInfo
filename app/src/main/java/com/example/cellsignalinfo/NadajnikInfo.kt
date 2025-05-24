package com.example.cellsignalinfo // Zmień na swoją nazwę pakietu

import com.google.gson.annotations.SerializedName

data class NadajnikInfo(
    @SerializedName("siec_id") val siecId: String?,
    @SerializedName("miejscowosc") val miejscowosc: String?,
    @SerializedName("lokalizacja") val lokalizacja: String?,
    @SerializedName("LATIuke") val latitude: Double?,    // Zwróć uwagę na wielkość liter w JSON
    @SerializedName("LONGuke") val longitude: Double?   // Zwróć uwagę na wielkość liter w JSON
)
