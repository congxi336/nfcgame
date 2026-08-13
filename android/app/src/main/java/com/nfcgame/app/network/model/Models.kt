package com.nfcgame.app.network.model

import com.google.gson.annotations.SerializedName

/**
 * 查询响应体。
 * 成功：{"code":200,"data":{"title":"...","content":"...","image_url":"..."}}
 * 失败：{"code":404,"message":"..."}
 */
data class InfoResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: InfoData? = null,
)

/** 隐藏信息数据 */
data class InfoData(
    @SerializedName("title") val title: String? = null,
    @SerializedName("content") val content: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("encrypted") val encrypted: Int? = 0,
    @SerializedName("attach_key") val attachKey: String? = null,
)

/** 保存请求体 */
data class SaveRequest(
    @SerializedName("uid") val uid: String,
    @SerializedName("title") val title: String,
    @SerializedName("content") val content: String,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("encrypted") val encrypted: Int = 0,
    @SerializedName("attach_key") val attachKey: String? = null,
)

/** 通用响应（保存/删除等返回 code + message） */
data class CommonResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String? = null,
)

/** 图片上传响应 */
data class UploadResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: UploadData? = null,
)

/** 上传成功返回的数据（相对路径 url） */
data class UploadData(
    @SerializedName("url") val url: String? = null,
)
