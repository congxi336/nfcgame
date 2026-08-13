package com.nfcgame.app.network

import com.nfcgame.app.network.model.InfoData
import okhttp3.MultipartBody

/**
 * 数据仓库：封装网络请求，统一异常处理，返回结果给 UI 层。
 */
object NfcRepository {

    sealed class Result<out T> {
        data class Success<T>(val data: T) : Result<T>()
        data class Error(val message: String) : Result<Nothing>()
    }

    /**
     * 查询 UID 对应的信息。
     * @return Success(InfoData?) —— data 为 null 表示未找到
     */
    suspend fun queryInfo(api: ApiService, uid: String): Result<InfoData?> {
        return try {
            val resp = api.getInfo(uid)
            if (resp.code == 200 && resp.data != null) {
                Result.Success(resp.data)
            } else if (resp.code == 404) {
                Result.Success(null) // 未找到，交由 UI 显示提示
            } else {
                Result.Error(resp.message ?: "查询失败（code=${resp.code}）")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络错误")
        }
    }

    /**
     * 保存信息。
     */
    suspend fun saveInfo(
        api: ApiService,
        uid: String,
        title: String,
        content: String,
        imageUrl: String?,
    ): Result<Unit> {
        return try {
            val resp = api.saveInfo(
                com.nfcgame.app.network.model.SaveRequest(
                    uid = uid,
                    title = title,
                    content = content,
                    imageUrl = imageUrl?.takeIf { it.isNotBlank() },
                )
            )
            if (resp.code == 200) Result.Success(Unit)
            else Result.Error(resp.message ?: "保存失败（code=${resp.code}）")
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络错误")
        }
    }

    /**
     * 上传图片，返回服务器可访问的相对路径（如 /uploads/xxx.png）。
     */
    suspend fun uploadImage(api: ApiService, file: MultipartBody.Part): Result<String> {
        return try {
            val resp = api.uploadImage(file)
            if (resp.code == 200 && resp.data?.url != null) {
                Result.Success(resp.data.url)
            } else {
                Result.Error(resp.message ?: "上传失败（code=${resp.code}）")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络错误")
        }
    }
}
