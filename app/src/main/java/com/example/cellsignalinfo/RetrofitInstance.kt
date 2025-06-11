package com.example.cellsignalinfo 

import com.example.cellsignalinfo.NadajnikiApiService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {
 
   
   private const val BASE_URL = "http://10.0.2.2/" 
    //private const val BASE_URL = "http://192.168.137.1/"
    val api: NadajnikiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NadajnikiApiService::class.java)
    }
}
