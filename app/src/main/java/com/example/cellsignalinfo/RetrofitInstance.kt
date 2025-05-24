package com.example.twoja_nazwa_pakietu // Zmień na swoją nazwę pakietu

import com.example.cellsignalinfo.NadajnikiApiService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {
    // ZMIEŃ TEN ADRES URL na adres Twojego lokalnego serwera!
    // Jeśli testujesz na emulatorze Androida, użyj 10.0.2.2 aby odnieść się do localhost Twojego komputera.
    // Jeśli testujesz na fizycznym urządzeniu, użyj lokalnego adresu IP Twojego komputera w sieci Wi-Fi (np. 192.168.1.100).
    // Upewnij się, że serwer PHP jest uruchomiony i dostępny pod tym adresem i portem.
    // Jeśli Twój skrypt PHP jest w podkatalogu, np. "api", to dodaj to do BASE_URL np. "http://10.0.2.2/api/"
    // lub dodaj do ścieżki w NadajnikiApiService @GET("api/nadajniki.php")
    private const val BASE_URL = "http://10.0.2.2/" // Przykładowy adres dla emulatora, jeśli PHP jest w roocie serwera

    val api: NadajnikiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NadajnikiApiService::class.java)
    }
}
