package com.jeahwan.simplehttp;

/**
 * 返回错误处理
 * Created by Makise on 2017/2/4.
 */

public class ResultException extends RuntimeException {
    public int errorCode;//接口请求可以在onError中取code做对应处理
    public Object data;

    public ResultException(BaseData baseResult) {
        super(baseResult.message);
        errorCode = baseResult.code;
        data = baseResult.data;
        handleException(baseResult);
    }

    /**
     * 处理返回异常信息
     */
    private void handleException(final BaseData baseResult) {
        switch (errorCode) {
            case 0:
                if (baseResult.data == null) {
                    //无数据异常
                    throw new NoDataException(baseResult.message);
                }
                break;
            case 3:
                //其他特殊异常
                break;
        }
    }

    public class NoDataException extends RuntimeException {
        public NoDataException(String message) {
            super(message);
        }
    }
}
