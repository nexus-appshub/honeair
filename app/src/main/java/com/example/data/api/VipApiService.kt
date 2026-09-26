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
    val merchantConfig: MerchantConfig? = null,
    val modalNotice: ModalNotice? = null,
    val premiumUsers: List<String> = emptyList(),
    val redeemCodes: List<RedeemCode> = emptyList(),
    val isMobilePaymentEnabled: Boolean = true,
    val mobilePaymentDisabledNote: String? = null,
    val externalPaymentUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class MerchantConfig(
    val bkashNumber: String? = null,
    val bkashType: String? = "Personal",
    val nagadNumber: String? = null,
    val nagadType: String? = "Personal",
    val rocketNumber: String? = null,
    val rocketType: String? = "Personal",
    val whatsappNumber: String? = null,
    val helplineNumber: String? = null,
    val merchantNotes: String? = null
)

@JsonClass(generateAdapter = true)
data class RedeemCode(
    val code: String = "",
    val planName: String? = null,
    val maxUses: Int = 1,
    val isActive: Boolean = true,
    val durationDays: Double? = null,
    val expiresAt: String? = null,
    val isLifetime: Boolean? = false,
    val usedCount: Int = 0
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

    @retrofit2.http.POST("api/vip/redeem")
    suspend fun redeemCode(@retrofit2.http.Body request: RedeemRequest): RedeemResponse
}

@JsonClass(generateAdapter = true)
data class RedeemRequest(
    val code: String,
    val email: String,
    val platform: String = "android"
)

@JsonClass(generateAdapter = true)
data class RedeemResponse(
    val success: Boolean,
    val message: String? = null,
    val planName: String? = null
)

object VipApiClient {
    val BASE_URL: String
        get() = com.example.network.AppConfigManager.DEFAULT_BACKEND_URL + "/"

    private val okHttpClient = OkHttpClient.Builder()
        .dispatcher(okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 16
        })
        .connectionPool(okhttp3.ConnectionPool(15, 5, TimeUnit.MINUTES))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val apiService: VipApiService by lazy {
        Retrofit.Builder()
            .baseUrl(com.example.network.AppConfigManager.DEFAULT_BACKEND_URL + "/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(VipApiService::class.java)
    }
}
