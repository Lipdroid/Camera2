package com.mdmunirhossain.camera2burst.Permissions;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;

/**
 * Created by mdmunirhossain on 5/19/17.
 */

public class RequestUserPermission {
    private Activity activity;
    // Storage Permissions
    public static final int REQUEST_PERMISSION = 1;
    String[] PERMISSIONS = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};


    public RequestUserPermission(Activity activity) {
        this.activity = activity;
    }

    public boolean verifyPermissions() {
        // Check if we have write permission
        int permission_camera = ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA);
        int permission_external = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission_camera != PackageManager.PERMISSION_GRANTED || permission_external != PackageManager.PERMISSION_GRANTED) {
            // no  permission,so ask permission
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS,
                    REQUEST_PERMISSION
            );
            return false;
        } else {
            return true;
        }
    }

}
