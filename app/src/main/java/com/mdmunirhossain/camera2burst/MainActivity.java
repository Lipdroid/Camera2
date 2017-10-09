package com.mdmunirhossain.camera2burst;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    private TextureView mTextureView;
    private Size mPreviewSize;
    private String mCameraId;
    private CameraDevice mCameraDevice;
    private CaptureRequest mPreviewCaptureRequest;
    private CaptureRequest.Builder mPreviewCaptureRequestBuilder;
    private CameraCaptureSession mCameraCaptureSession;

    /*To get the height width of a textureview which is important
        for showing the camera on screen
        set a surfaceTextureListener
    */
    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //when availabel getting the height and width
            //so set up the camera
            setupCamera(width, height);
            //open the camera
            openCamera();

        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    /*setting camera state callback
    * so we can know camera is open or not
    * */
    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            //we got our camera device
            mCameraDevice = camera;
            //now set the camera to preiview in our textureview
            //create a previewSession
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }
    };

    /*
    * session request callback
    * */
    private CameraCaptureSession.CaptureCallback mSessionCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //finiding views
        mTextureView = (TextureView) findViewById(R.id.textureView);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mTextureView.isAvailable()) {

        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    private void setupCamera(int width, int height) {
        //get the camera manager
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        //accessing camera thats why include try and catch
        try {
            //get all the cameras by its ids
            for (String cameraId : cameraManager.getCameraIdList()) {
                //get the camera characteristics of that cameraid
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                //check if its front facing camera
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                //get the front facing camera so save the camera id
                mCameraId = cameraId;
                //now get the supported sizes of that camera to set up in the textureview for preview
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                //get the closest minimum size of the camera supported and set it to our textureview
                mPreviewSize = getPreferredPreviewSize(map.getOutputSizes(SurfaceTexture.class), width, height);

                return;

            }

        } catch (CameraAccessException ex) {
            ex.printStackTrace();
        }

    }

    private Size getPreferredPreviewSize(Size[] mapSizes, int width, int height) {

        List<Size> collectorSizes = new ArrayList<>();
        for (Size option : mapSizes) {
            //for landscape
            if (width > height) {
                if (option.getWidth() > width && option.getHeight() > height) {
                    //valid size
                    collectorSizes.add(option);
                }
            } else {
                //for potrait
                if (option.getWidth() > height && option.getHeight() > width) {
                    //valid size
                    collectorSizes.add(option);
                }
            }
        }

        if (collectorSizes.size() > 0) {
            //return the minimum size
            return Collections.min(collectorSizes, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getWidth() * rhs.getHeight());
                }
            });
        }
        return mapSizes[0];
    }

    private void openCamera() {
        //get the camera manager
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        //accessing camera thats why include try and catch
        try {
            //checking permisions
            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            //this is going to open the camera bt will not visible cause we are not yet set with the texture view
            //it will call camera opened callback
            cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, null);

        } catch (CameraAccessException ex) {
            ex.printStackTrace();
        }
    }

    private void createCameraPreviewSession() {
        //accessing camera thats why include try and catch
        try {
            //get the surface of our textureView
            SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
            //set the size we get from calculating minimum sizes
            surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            //create a surface from that surface texture which is needed for camera capture request
            Surface previewSurface = new Surface(surfaceTexture);
            //create the capture request Builder
            mPreviewCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            //add the surface
            mPreviewCaptureRequestBuilder.addTarget(previewSurface);
            //now create capture session
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    //camera is still available build the request
                    if (mCameraDevice == null) {
                        return;
                    }
                    try {
                        mPreviewCaptureRequest = mPreviewCaptureRequestBuilder.build();
                        //store the session
                        mCameraCaptureSession = session;
                        //request the session to start
                        mCameraCaptureSession.setRepeatingBurst(Arrays.asList(mPreviewCaptureRequest), mSessionCaptureCallback, null);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e("OnCameraSession: ","failed");
                }
            }, null);

        } catch (CameraAccessException ex) {
            ex.printStackTrace();
        }
    }

}
