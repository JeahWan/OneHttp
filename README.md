# OneHttp

简单易用的网络请求库封装，基于retrofit+rxjava

//网络库

kapt 'com.jeahwan.library:onehttp-compiler:1.0.0'

compileOnly 'com.jeahwan.library:onehttp-compiler:1.0.0'

implementation 'com.jeahwan.library:onehttp:1.0.0'

添加混淆：

# onehttp
-keep class com.jeahwan.onehttp.HttpResult{*;}
