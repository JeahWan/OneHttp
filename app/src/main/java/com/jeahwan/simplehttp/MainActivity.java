package com.jeahwan.simplehttp;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.jeahwan.onehttp.HttpMethods;

import java.util.concurrent.ConcurrentHashMap;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        findViewById(R.id.btn).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                HttpMethods.class
//                        .params(new ConcurrentHashMap<String, Object>() {{
//                            put("type", "1");
//                            put("page", "1");
//                        }})
//                        .apiDemo(new ProgressSubscriber<String>(true) {
//                            @Override
//                            public void onSuccess(String jsonStr) {
//                                ((TextView) findViewById(R.id.tv)).setText(jsonStr);
//                            }
//                        });
//            }
//        });
    }
}
