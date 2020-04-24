package com.jeahwan.simplehttp;

import java.util.Map;

import io.reactivex.Observable;
import io.reactivex.Observer;
import retrofit2.http.FieldMap;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.POST;

/**
 * 网络请求的契约类
 * Created by Makise on 2017/2/4.
 */

public interface HttpContract {
    interface Services {

        /**
         * 测试api
         *
         * @param param
         * @return
         */
        @FormUrlEncoded
        @POST("course/listByParameter")
        Observable<BaseData<String>> apiDemo(@FieldMap Map<String, Object> param);
    }

    interface Methods {
        void apiDemo(Observer<String> subscriber);
    }
}
