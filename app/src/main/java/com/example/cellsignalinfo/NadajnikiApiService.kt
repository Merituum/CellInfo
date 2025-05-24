package com.example.cellsignalinfo // Zmień na swoją nazwę pakietu

import retrofit2.Response
import retrofit2.http.GET

interface NadajnikiApiService {
    @GET("get_nadajniki.php") // Np. "api/nadajniki.php" lub po prostu "nadajniki.php" jeśli jest w roocie
    suspend fun getNadajniki(): Response<List<NadajnikInfo>>
}
