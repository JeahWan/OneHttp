package com.jeahwan.onehttp

import java.io.Serializable

class HttpResult<T> : Serializable {
    //根据服务器返回数据结构定义
    var message: String? = null
    var code = 0

    //泛型指定返回的数据结构 如自定义bean、List、String[]等任意
    var data: T = TODO()

    /**
     * 返回错误处理
     * Created by Makise on 2017/2/4.
     */
    class Exception(httpResult: HttpResult<*>) : RuntimeException(httpResult.message) {
        //接口请求可以在onError中取code做对应处理
        var errorCode: Int = httpResult.code
        var data: Any? = httpResult.data

        /**
         * 处理返回异常信息
         */
        init {
            OneHttpManager.errorCodeHandle.forEach {
                if (it.key == errorCode) {
                    it.value.invoke()
                }
            }
            when (errorCode) {
                //登录过期 去登录页
//                3 -> RxTask.doInMainThread { LoginUtils.logoutNow() }
            }
        }
    }
}