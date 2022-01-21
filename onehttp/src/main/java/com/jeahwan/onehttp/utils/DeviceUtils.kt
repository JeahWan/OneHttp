package com.jeahwan.onehttp.utils

import android.Manifest.permission
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.TextUtils
import androidx.annotation.RequiresPermission
import com.jeahwan.onehttp.OneHttpManager

/**
 * <pre>
 * author: Blankj
 * blog  : http://blankj.com
 * time  : 2016/8/1
 * desc  : utils about device
</pre> *
 */
object DeviceUtils {

    /**
     * Return version code of device's system.
     *
     * @return version code of device's system
     */
    fun getSDKVersionCode(): Int {
        return Build.VERSION.SDK_INT
    }

    /**
     * Return the android id of device.
     *
     * @return the android id of device
     */
    @get:SuppressLint("HardwareIds")
    val androidID: String
        get() {
            val id = Settings.Secure.getString(
                OneHttpManager.context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            return id ?: ""
        }

    /**
     * Return the model of device.
     *
     * e.g. MI2SC
     *
     * @return the model of device
     */
    fun getModel(): String {
        var model = Build.MODEL
        model = model?.trim { it <= ' ' }?.replace("\\s*".toRegex(), "") ?: ""
        return model
    }

    fun getBrand(): String {
        var brand = Build.BRAND
        brand = brand?.trim { it <= ' ' }?.replace("\\s*".toRegex(), "") ?: ""
        return brand
    }

    /**
     * Return the IMEI.
     *
     * If the version of SDK is greater than 28, it will return an empty string.
     *
     * Must hold `<uses-permission android:name="android.permission.READ_PHONE_STATE" />`
     *
     * @return the IMEI
     */
    @get:RequiresPermission(permission.READ_PHONE_STATE)
    val iMEI: String?
        get() = getImeiOrMeid(true)

    @SuppressLint("HardwareIds")
    @RequiresPermission(permission.READ_PHONE_STATE)
    fun getImeiOrMeid(isImei: Boolean): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ""
        }
        val tm = telephonyManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return if (isImei) {
                getMinOne(tm.getImei(0), tm.getImei(1))
            } else {
                getMinOne(tm.getMeid(0), tm.getMeid(1))
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val ids =
                getSystemPropertyByReflect(if (isImei) "ril.gsm.imei" else "ril.cdma.meid")
            if (!TextUtils.isEmpty(ids)) {
                val idArr = ids.split(",".toRegex()).toTypedArray()
                return if (idArr.size == 2) {
                    getMinOne(idArr[0], idArr[1])
                } else {
                    idArr[0]
                }
            }
            var id0 = tm.deviceId
            var id1: String? = ""
            try {
                val method = tm.javaClass.getMethod("getDeviceId", Int::class.javaPrimitiveType)
                id1 = method.invoke(
                    tm,
                    if (isImei) TelephonyManager.PHONE_TYPE_GSM else TelephonyManager.PHONE_TYPE_CDMA
                ) as String?
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (isImei) {
                if (id0 != null && id0.length < 15) {
                    id0 = ""
                }
                if (id1 != null && id1.length < 15) {
                    id1 = ""
                }
            } else {
                if (id0 != null && id0.length == 14) {
                    id0 = ""
                }
                if (id1 != null && id1.length == 14) {
                    id1 = ""
                }
            }
            return getMinOne(id0, id1)
        } else {
            val deviceId = tm.deviceId
            if (isImei) {
                if (deviceId != null && deviceId.length >= 15) {
                    return deviceId
                }
            } else {
                if (deviceId != null && deviceId.length == 14) {
                    return deviceId
                }
            }
        }
        return ""
    }

    private fun getMinOne(s0: String?, s1: String?): String? {
        val empty0 = TextUtils.isEmpty(s0)
        val empty1 = TextUtils.isEmpty(s1)
        if (empty0 && empty1) return ""
        if (!empty0 && !empty1) {
            return if (s0!!.compareTo(s1!!) <= 0) {
                s0
            } else {
                s1
            }
        }
        return if (!empty0) s0 else s1
    }

    private val telephonyManager: TelephonyManager
        get() = OneHttpManager.context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private fun getSystemPropertyByReflect(key: String): String {
        try {
            @SuppressLint("PrivateApi") val clz = Class.forName("android.os.SystemProperties")
            val getMethod = clz.getMethod("get", String::class.java, String::class.java)
            return getMethod.invoke(clz, key, "") as String
        } catch (e: Exception) { /**/
        }
        return ""
    }

    /**
     * 当前是否是鸿蒙系统
     * 根据是否能调用Harmony JAVA API判断
     */
    fun isHarmonyOs(): Boolean {
        return try {
            Class.forName("ohos.utils.system.SystemCapability")
            true
        } catch (e: Exception) {
            false
        }
    }
}