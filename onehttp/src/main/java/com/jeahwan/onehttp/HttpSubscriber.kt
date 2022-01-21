package com.jeahwan.onehttp

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import io.reactivex.rxjava3.observers.DisposableObserver
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/**
 * 用于在Http请求开始时，自动显示一个ProgressDialog
 * 在Http请求结束是，关闭ProgressDialog
 * 调用者自己对请求数据进行处理
 * Created by Makise on 16/8/2.
 */
abstract class HttpSubscriber<T> constructor(needProgress: Boolean = true) :
    DisposableObserver<T>() {
    private var showToast = true
    private var mLoadingHandler: LoadingHandler? = null

    /**
     * 调用此方法隐藏toast提示
     *
     * @return
     */
    fun hideToast(): HttpSubscriber<*> {
        showToast = false
        return this
    }

    private fun showProgressDialog() {
        if (mLoadingHandler != null) {
            mLoadingHandler!!.obtainMessage(LoadingHandler.SHOW_PROGRESS_DIALOG)
                .sendToTarget()
        }
    }

    private fun dismissProgressDialog() {
        if (mLoadingHandler != null) {
            mLoadingHandler!!.obtainMessage(LoadingHandler.DISMISS_PROGRESS_DIALOG)
                .sendToTarget()
            mLoadingHandler = null
        }
        if (!this.isDisposed) {
            dispose()
        }
    }

    /**
     * 订阅开始时调用
     * 显示ProgressDialog
     */
    public override fun onStart() {
        showProgressDialog()
    }

    /**
     * 完成，隐藏ProgressDialog
     */
    override fun onComplete() {
        dismissProgressDialog()
    }

    /**
     * 请求成功
     * 只是想换个方法名 所以包装一下
     *
     * @param t
     */
    override fun onNext(t: T) {
        onSuccess(t)
    }

    abstract fun onSuccess(data: T)

    /**
     * 对错误进行统一处理
     * 隐藏ProgressDialog
     *
     * @param throwable
     */
    override fun onError(throwable: Throwable) {
        dismissProgressDialog()
        if (!isNetworkConnected()) {
            Toast.makeText(OneHttpManager.context, "网络异常，请检查您的网络", Toast.LENGTH_SHORT).show()
            return
        }
        //网络异常的不弹出
        when (throwable) {
            is ConnectException,
            is UnknownHostException,
            is SSLHandshakeException,
            is SocketTimeoutException -> return
            is HttpResult.Exception -> when (throwable.errorCode) {
                //不显示toast的code
                in (OneHttpManager.hideToastCode) -> return
            }
        }
        //非空 toast
        if (!throwable.message.isNullOrBlank() && showToast)
            Toast.makeText(OneHttpManager.context, throwable.message, Toast.LENGTH_SHORT).show()
    }

    init {
        //默认弹toast
        if (needProgress) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                //主线程创建handler
                mLoadingHandler = LoadingHandler(OneHttpManager.curActivity(), true)
            }
        }
    }

    /**
     * 网络请求时显示的loading
     * Created by Makise on 16/8/2.
     */
    class LoadingHandler(private val context: Context?, private val cancelable: Boolean) :
        Handler() {
        private var dialog: AlertDialog? = null
        private fun initProgressDialog() {
            if (context == null) {
                return
            }
            if (context is Activity && context.isFinishing) {
                return
            }
            if (context is FragmentActivity && context.supportFragmentManager.isDestroyed) {
                return
            }
            if (dialog == null) {
                val builder: AlertDialog.Builder =
                    AlertDialog.Builder(context, R.style.dialogTransparent)
                dialog = builder.create()
                dialog?.setCanceledOnTouchOutside(false)
                dialog?.setCancelable(cancelable)
            }
            val view = View.inflate(context, R.layout.dialog_loading, null)
            val loadingView = view.findViewById<ImageView>(R.id.iv_load)
            val operatingAnim = AnimationUtils.loadAnimation(
                context, R.anim.rotate_loading
            )
            operatingAnim.interpolator = LinearInterpolator()
            loadingView.startAnimation(operatingAnim)
            if (!dialog!!.isShowing) {
                if (context is Activity && context.isFinishing) {
                    return
                }
                dialog!!.show()
            }
            dialog!!.window!!.setContentView(view)
        }

        private fun dismissProgressDialog() {
            if (context == null) {
                return
            }
            if (context is Activity && context.isFinishing) {
                return
            }
            if (context is FragmentActivity && context.supportFragmentManager.isDestroyed) {
                return
            }
            if (dialog != null && dialog!!.isShowing) {
                dialog!!.dismiss()
            }
        }

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                SHOW_PROGRESS_DIALOG -> initProgressDialog()
                DISMISS_PROGRESS_DIALOG -> dismissProgressDialog()
            }
        }

        companion object {
            const val SHOW_PROGRESS_DIALOG = 1
            const val DISMISS_PROGRESS_DIALOG = 2
        }
    }

    /**
     * 判断手机是否开启网络 wifi或3G 有一个开着就为true
     *
     * @param context
     * @return
     */
    private fun isNetworkConnected(): Boolean {
        val mConnectivityManager = OneHttpManager.context
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val mNetworkInfo = mConnectivityManager.activeNetworkInfo
        if (mNetworkInfo != null) {
            return mNetworkInfo.isAvailable
        }
        return false
    }
}