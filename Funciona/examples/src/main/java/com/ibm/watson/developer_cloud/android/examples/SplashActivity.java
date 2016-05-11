package com.ibm.watson.developer_cloud.android.examples;

import android.app.PendingIntent;
import android.os.Handler;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;



public class SplashActivity extends AppCompatActivity {
    private final int SPLASH= 2000;
    private PendingIntent pendingIntent;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);


        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent it = new Intent(SplashActivity.this, MainActivity.class);
                startActivity(it);
            }
        }, SPLASH);
    }
}
