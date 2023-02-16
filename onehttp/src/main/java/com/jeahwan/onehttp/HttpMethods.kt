package com.jeahwan.onehttp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import androidx.core.app.ActivityCompat
import com.jeahwan.onehttp.utils.DeviceUtils
import com.jeahwan.onehttp.utils.MD5HexHelper
import com.google.gson.Gson
import com.ihsanbal.logging.Level
import com.ihsanbal.logging.LoggingInterceptor
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.platform.Platform
import okio.Buffer
import okio.BufferedSink
import okio.GzipSink
import okio.buffer
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.util.*
import java.util.concurrent.TimeUnit


/**
 * 网络请求基类
 * Created by Makise on 2017/2/4.
 */
open class HttpMethods {
    private val okHttpClient: OkHttpClient

    lateinit var mService: Any

    /**
     * 修改mService 用于切换环境
     */
    fun createService() {
        mService = Retrofit.Builder()
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
            .baseUrl(OneHttpManager.baseUrl())
            .build()
            .create(OneHttpManager.services)
    }

    /**
     * 添加公共参数
     *
     * @param request
     * @return
     */
    private fun addParams(request: Request): Map<String, Any> {
        val params: MutableMap<String, Any> = HashMap()
        when (request.method) {
            "GET" -> {
                val requestUrl = request.url
                var i = 0
                while (i < requestUrl.querySize) {
                    params[requestUrl.queryParameterName(i)] =
                        requestUrl.queryParameterValue(i).toString()
                    i++
                }
            }
            "POST", "PUT" -> {
                val formBody = request.body as FormBody?
                var i = 0
                while (i < formBody!!.size) {
                    try {
                        params[formBody.encodedName(i)] =
                            URLDecoder.decode(formBody.encodedValue(i), "UTF-8")
                    } catch (e: UnsupportedEncodingException) {
                        e.printStackTrace()
                    }
                    i++
                }
            }
        }
        params["osName"] = if (DeviceUtils.isHarmonyOs()) "HarmonyOs" else "android"
        params["osVersion"] =
            if (DeviceUtils.isHarmonyOs()) Build.DISPLAY else DeviceUtils.getSDKVersionCode()
        params["deviceId"] = DeviceUtils.androidID
        params["deviceBrand"] = DeviceUtils.getBrand()
        params["deviceModel"] = DeviceUtils.getModel()
        if ((ActivityCompat.checkSelfPermission(
                OneHttpManager.context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED) && !DeviceUtils.iMEI.isNullOrEmpty()
        ) {
            params["imei"] = DeviceUtils.iMEI!!
        }
        OneHttpManager.body().forEach {
            params[it.key] = it.value
        }
        return params
    }

    init {
        val clientBuilder = OkHttpClient.Builder()
            //设置统一的User-Agent
            .addInterceptor(Interceptor { chain: Interceptor.Chain ->
                //添加公共参数
                val params = addParams(chain.request())
                //埋点接口加上gzip压缩
                val isOpenGZip = OneHttpManager.gZipUrl.contains(chain.request().url.encodedPath)
                //添加headers
                val time = String.format("%010d", System.currentTimeMillis() / 1000)
                val builder: Request.Builder = chain.request().newBuilder()
                    .addHeader("token", OneHttpManager.token())
                    .addHeader("timestamp", time)
                    .addHeader("osName", "android")
                    .addHeader("content-type", "application/json; charset=utf-8")
                    .addHeader("signature", getSign(params, time, chain.request().url.encodedPath))
                if (isOpenGZip) {
                    builder.addHeader("Content-Encoding", "gzip")
                }
                OneHttpManager.header().forEach {
                    builder.addHeader(it.key, it.value)
                }
                //设置MediaType
                val requestBody: RequestBody = try {
                    Gson().toJson(params)
                        .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                } catch (e: Exception) {
                    JSONObject(params).toString()
                        .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                }
                when (chain.request().method) {
                    "GET" -> builder.get()
                    "POST" -> builder.post(
                        if (isOpenGZip) {
                            try {
                                computeContentLength(gzip(requestBody))
                            } catch (e: Exception) {
                                gzip(requestBody)
                            }
                        } else requestBody
                    )
                    "PUT" -> builder.put(requestBody)
                }
                chain.proceed(builder.build())
            })
            .connectTimeout(60L, TimeUnit.SECONDS)
            .writeTimeout(60L, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(8, 15, TimeUnit.SECONDS))
        takeIf { OneHttpManager.debugMode }?.apply {
            clientBuilder
                //LoggingInterceptor
                .addInterceptor(
                    LoggingInterceptor.Builder() //构建者模式
                        .setLevel(Level.BASIC) //打印的等级，非debug包不打印
                        .log(Platform.INFO) // 打印类型
                        .request("OneHttp-Request") // request的Tag
                        .response("OneHttp-Response") // Response的Tag
                        .build()
                )
                //Chuck
//                .addInterceptor(ChuckerInterceptor(OneHttpManager.context))
        }
        okHttpClient = clientBuilder.build()
        createService()
    }

    private fun formatUrlMap(paraMap: Map<String, Any>): String? {
        val buff: String
        try {
            val infoIds: List<Map.Entry<String, Any>> = ArrayList(paraMap.entries)
            // 对所有传入参数按照字段名的 ASCII 码从小到大排序（字典序）
            Collections.sort(infoIds) { o1, o2 -> (o1.key).compareTo(o2.key) }
            // 构造URL 键值对的格式
            val buf = StringBuilder()
            for (item: Map.Entry<String, Any> in infoIds) {
                buf.append(item.key + item.value)
            }
            buff = buf.toString()
        } catch (e: Exception) {
            return null
        }
        return buff
    }

    private fun getSign(
        paraMap: Map<String, Any>,
        time: String,
        path: String,
    ): String {
        val url = formatUrlMap(paraMap)
        val data = Base64.encodeToString(
            time.toByteArray(),
            Base64.NO_WRAP
        ) + OneHttpManager.token() + OneHttpManager.apiSecret(path) + url
        return MD5HexHelper.toMD5(
            data.replace("\\[".toRegex(), "").replace("]".toRegex(), "").replace("\"".toRegex(), "")
        )
    }

    @Throws(IOException::class)
    private fun computeContentLength(requestBody: RequestBody): RequestBody {
        val buffer = Buffer()
        requestBody.writeTo(buffer)
        return object : RequestBody() {
            override fun contentType(): MediaType? {
                return requestBody.contentType()
            }

            override fun contentLength(): Long {
                return buffer.size
            }

            @Throws(IOException::class)
            override fun writeTo(sink: BufferedSink) {
                try {
                    sink.write(buffer.snapshot())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun gzip(body: RequestBody): RequestBody {
        return object : RequestBody() {
            override fun contentType(): MediaType? {
                return body.contentType()
            }

            override fun contentLength(): Long {
                return -1
            }

            @Throws(IOException::class)
            override fun writeTo(sink: BufferedSink) {
                try {
                    val gzipSink: BufferedSink = GzipSink(sink).buffer()
                    body.writeTo(gzipSink)
                    gzipSink.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}