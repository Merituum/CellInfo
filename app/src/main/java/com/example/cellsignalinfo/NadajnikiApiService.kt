package com.example.cellsignalinfo 

import retrofit2.Response
import retrofit2.http.GET

interface NadajnikiApiService {
    @GET("get_nadajniki.php") 
    suspend fun getNadajniki(): Response<List<NadajnikInfo>>
}
