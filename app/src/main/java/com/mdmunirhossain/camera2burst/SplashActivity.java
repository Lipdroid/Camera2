package com.mdmunirhossain.camera2burst;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.mdmunirhossain.camera2burst.Permissions.RequestUserPermission;

public class SplashActivity extends AppCompatActivity {
    Handler handler = null;
    private long SPLASH_SCREEN_TIME = 2000;
    private String TAG = "SplashActivity";
    private Context mContext = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        mContext = this;
        //Request for camera permission
        RequestUserPermission requestUserPermission = new RequestUserPermission(this);
        boolean is_camera_permission_granted = requestUserPermission.verifyPermissions();

        //check already permitted
        if (is_camera_permission_granted) {

            runSplash();
        }


    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case RequestUserPermission.REQUEST_PERMISSION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    runSplash();

                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    finish();
                }
                return;
            }

        }
    }

    private void runSplash() {
        {
            handler = new Handler();
            Runnable run = new Runnable() {
                @Override
                public void run() {
                    goToMainPage();

                }
            };
            handler.postDelayed(run, SPLASH_SCREEN_TIME);
        }
    }

    private void goToMainPage() {
        startActivity(new Intent(SplashActivity.this, MainActivity.class));

    }

}
