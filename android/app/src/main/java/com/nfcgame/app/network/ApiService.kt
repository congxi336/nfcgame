package com.nfcgame.app.network

import com.nfcgame.app.network.model.CommonResponse
import com.nfcgame.app.network.model.InfoResponse
import com.nfcgame.app.network.model.SaveRequest
import com.nfcgame.app.network.model.UploadResponse
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * 服务器 API 接口定义（Retrofit）。
 */
interface ApiService {

    /**
     * 查询指定 UID 的信息。
     */
    @GET("/api/info")
    suspend fun getInfo(@Query("uid") uid: String): InfoResponse

    /**
     * 新增或更新一条信息。
     */
    @POST("/api/info")
    suspend fun saveInfo(@Body body: SaveRequest): CommonResponse

    /**
     * 上传图片（multipart/form-data），返回可访问的相对路径。
     */
    @Multipart
    @POST("/api/upload")
    suspend fun uploadImage(@Part file: MultipartBody.Part): UploadResponse
}
