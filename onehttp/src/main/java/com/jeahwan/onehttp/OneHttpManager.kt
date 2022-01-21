package com.jeahwan.onehttp

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import com.chuckerteam.chucker.api.Chucker
import com.chuckerteam.chucker.api.ChuckerInterceptor

@SuppressLint("StaticFieldLeak")
object OneHttpManager {
    lateinit var context: Context
    var curActivity: () -> Activity? = { null }
    lateinit var baseUrl: () -> String
    var token: () -> String = { "" }
    var apiSecret: (path: String) -> String = { "" } //接口对应的签名干扰码
    var header: () -> Map<String, String> = { mapOf() }
    var hideToastCode: IntArray = intArrayOf()
    var debugMode: Boolean = false
    var gZipUrl: List<String> = listOf() //开启gzip压缩的url

    lateinit var services: Class<*>
    lateinit var body: () -> Map<String, Any>
    lateinit var errorCodeHandle: Map<Int, () -> Unit>

    fun openChuck() {
        context.startActivity(
            Chucker.getLaunchIntent(
                context
            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        )
    }

    fun getChuck(context: Context): okhttp3.Interceptor = ChuckerInterceptor(context)
}