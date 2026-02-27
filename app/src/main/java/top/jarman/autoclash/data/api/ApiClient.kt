package top.jarman.autoclash.data.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    @Volatile
    private var currentBaseUrl: String? = null

    @Volatile
    private var currentSecret: String? = null

    @Volatile
    private var api: MihomoApi? = null

    fun getApi(baseUrl: String, secret: String): MihomoApi {
        if (api != null && currentBaseUrl == baseUrl && currentSecret == secret) {
            return api!!
        }
        synchronized(this) {
            if (api != null && currentBaseUrl == baseUrl && currentSecret == secret) {
                return api!!
            }

            val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

            val authInterceptor = Interceptor { chain ->
                val request = if (secret.isNotBlank()) {
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $secret")
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }

            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(loggingInterceptor)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            api = retrofit.create(MihomoApi::class.java)
            currentBaseUrl = baseUrl
            currentSecret = secret
            return api!!
        }
    }

    fun clearApi() {
        synchronized(this) {
            api = null
            currentBaseUrl = null
            currentSecret = null
        }
    }
}
