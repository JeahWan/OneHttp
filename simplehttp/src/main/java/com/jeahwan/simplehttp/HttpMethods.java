package com.jeahwan.simplehttp;

import java.util.concurrent.ConcurrentHashMap;

import io.reactivex.Observable;
import io.reactivex.Observer;


/**
 * 网络请求的方法实现
 * Created by Makise on 2017/2/4.
 */

public class HttpMethods extends BaseHttpMethods implements HttpContract.Methods {

    private static HttpMethods INSTANCE;
    private ConcurrentHashMap<String, Object> mParams = new ConcurrentHashMap<>();

    //构造方法私有
    private HttpMethods() {
    }

    //获取单例
    public static HttpMethods getInstance() {
        if (INSTANCE == null) {
            synchronized (HttpMethods.class) {
                if (INSTANCE == null) {
                    INSTANCE = new HttpMethods();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 设置请求参数 没有传null即可
     *
     * @param params
     * @return
     */
    public HttpMethods params(ConcurrentHashMap<String, Object> params) {
        if (params != null) {
            mParams = params;
        }
        return this;
    }

    /**
     * 以下开始实现具体的网络请求方法
     */

    @Override
    public void apiDemo(Observer<String> subscriber) {
        Observable observable = mService.apiDemo(mParams).map(new HttpResultFunc());
        toSubscribe(observable, subscriber);
    }
}