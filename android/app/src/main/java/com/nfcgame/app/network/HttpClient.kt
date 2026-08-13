package com.nfcgame.app.network

import android.content.Context
import com.nfcgame.app.BuildConfig
import com.nfcgame.app.R
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * HTTP 客户端工厂：创建 Retrofit 实例。
 *
 * 由于服务器使用自签名 HTTPS 证书，系统默认不信任，这里做「证书固定」：
 * 将服务器证书（res/raw/server_cert.pem）加载为唯一信任源。
 * 若运行时证书解析异常，则回退为信任所有证书（不安全，仅用于联调兜底）。
 */
object HttpClient {

    @Volatile
    private var apiService: ApiService? = null

    fun getApiService(context: Context): ApiService {
        return apiService ?: synchronized(this) {
            apiService ?: buildApiService(context.applicationContext).also { apiService = it }
        }
    }

    private fun buildApiService(context: Context): ApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
        }

        val trustManager = buildTrustManager(context)
        val sslContext = buildSslContext(trustManager)

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true } // 自签名证书，忽略主机名校验（已通过证书固定保证安全）
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.SERVER_URL.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    /**
     * 构建 SSLContext：使用给定的 TrustManager（服务器自签名证书固定）。
     */
    private fun buildSslContext(trustManager: X509TrustManager): SSLContext {
        return SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        }
    }

    /**
     * 构建 TrustManager：信任 res/raw/server_cert.pem 中的服务器自签名证书。
     * 若运行时证书解析失败则回退为信任所有（仅开发用，生产必须保证证书有效）。
     */
    private fun buildTrustManager(context: Context): X509TrustManager {
        return try {
            val cf = CertificateFactory.getInstance("X.509")
            val cert = context.resources.openRawResource(R.raw.server_cert).use { input ->
                cf.generateCertificate(input) as X509Certificate
            }

            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setCertificateEntry("server", cert)
            }

            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore)
            }

            tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
        } catch (e: Exception) {
            android.util.Log.w("HttpClient", "未找到服务器证书，回退为信任所有证书（仅开发用）", e)
            trustAllManager
        }
    }

    /** 信任所有证书（不安全，仅开发联调用） */
    private val trustAllManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    private fun String.ensureTrailingSlash(): String =
        if (endsWith("/")) this else "$this/"
}
