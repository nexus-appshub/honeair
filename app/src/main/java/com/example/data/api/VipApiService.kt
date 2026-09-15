package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

@JsonClass(generateAdapter = true)
data class VipConfigResponse(
    val success: Boolean = true,
    val version: String? = "2.0",
    val pricingPlans: List<VipPlan> = emptyList(),
    val paymentGateways: PaymentGateways? = null,
    val modalNotice: ModalNotice? = null
)

@JsonClass(generateAdapter = true)
data class VipPlan(
    val id: String = "",
    val name: String = "",
    val duration: String = "",
    val priceBDT: Int = 0,
    val originalPriceBDT: Int? = null,
    val isPopular: Boolean = false,
    val badge: String? = null,
    val features: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class PaymentGateways(
    val bkash: GatewayInfo? = null,
    val nagad: GatewayInfo? = null,
    val rocket: GatewayInfo? = null,
    val whatsapp: String? = null
)

@JsonClass(generateAdapter = true)
data class GatewayInfo(
    val number: String = "",
    val type: String = "Personal"
)

@JsonClass(generateAdapter = true)
data class ModalNotice(
    val title: String = "",
    val subtitle: String = "",
    val supportWhatsApp: String? = null
)

@JsonClass(generateAdapter = true)
data class VipStatusResponse(
    val success: Boolean = false,
    val isPremium: Boolean = false,
    val expiryDate: String? = null,
    val isExpired: Boolean = false,
    val planName: String? = null,
    val message: String? = null
)

interface VipApiService {
    @GET("api/vip/config")
    suspend fun getVipConfig(): VipConfigResponse

    @GET("api/vip/status")
    suspend fun getVipStatus(@Query("email") email: String): VipStatusResponse
}

object VipApiClient {
    private const val BASE_URL = "https://ais-pre-nuksdkebkdtr6vurgwnjt6-78196958187.asia-east1.run.app/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    val apiService: VipApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(VipApiService::class.java)
    }
}
