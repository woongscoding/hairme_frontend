package com.example.myapplication.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface UsageApiService {

    @GET("/api/usage")
    suspend fun getUsage(
        @Query("device_id") deviceId: String
    ): Response<UsageResponse>

    @POST("/api/usage/consume")
    suspend fun consumeUsage(
        @Query("device_id") deviceId: String
    ): Response<UsageResponse>
}

data class UsageResponse(
    @SerializedName("daily_limit")
    val dailyLimit: Int,
    val used: Int,
    val remaining: Int
)
