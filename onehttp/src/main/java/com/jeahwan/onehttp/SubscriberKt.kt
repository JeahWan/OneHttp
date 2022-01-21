package com.jeahwan.onehttp

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.TimeUnit

object SubscriberKt {
    /**
     * 添加线程管理并订阅
     *
     * @param o   被观察者
     * @param s   订阅者
     * @param <T> 具体的泛型类型
    </T> */
    inline fun <reified T : Any> toSubscribe(
        o: Observable<HttpResult<T>>,
        crossinline onSucc: (`data`: T) -> Unit,
        noinline onError: ((t: Throwable) -> Unit)? = null,
        needLoading: Boolean = true,
        overrideErrorSuper: Boolean = true
    ) {
        //统一处理Http的resultCode,并将HttpResult的Data部分剥离出来返回给subscriber
        o.map { httpResult: HttpResult<T> ->
            //异常处理
            if (httpResult.code != 0) throw HttpResult.Exception(httpResult)
            //解决rxjava null传递问题
            if (httpResult.data == null) {
                httpResult.data = T::class.java.newInstance()
            }
            httpResult.data
        }.subscribeOn(Schedulers.io())
            .unsubscribeOn(Schedulers.io()) //防手抖
            .throttleFirst(2, TimeUnit.SECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : HttpSubscriber<T>(needLoading) {
                override fun onSuccess(`data`: T) {
                    onSucc.invoke(data)
                }

                override fun onError(throwable: Throwable) {
                    takeIf { overrideErrorSuper }?.apply { super.onError(throwable) }
                    onError?.invoke(throwable)
                }
            })
    }
}