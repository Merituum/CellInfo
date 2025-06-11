package com.example.cellsignalinfo 

import com.google.gson.annotations.SerializedName

data class NadajnikInfo(
    @SerializedName("siec_id") val siecId: String?,
    @SerializedName("miejscowosc") val miejscowosc: String?,
    @SerializedName("lokalizacja") val lokalizacja: String?,
    @SerializedName("LATIuke") val latitude: Double?,    
    @SerializedName("LONGuke") val longitude: Double?   
)
