package com.mdmunirhossain.camera2burst;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Environment;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import android.os.Handler;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    private TextView tv_count;

    private TextureView mTextureView;
    private Size mPreviewSize;
    private String mCameraId;
    private CameraDevice mCameraDevice;
    private CaptureRequest mPreviewCaptureRequest;
    private CaptureRequest.Builder mPreviewCaptureRequestBuilder;
    private CameraCaptureSession mCameraCaptureSession;

    //create back
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    //Focus States
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAIT_LOCK = 1;
    private int mState;

    //file to save image
    private static File mImageFile;
    private String mImageFileLocation = "";
    private String GALLERY_LOCATION = "BurstShot";
    private File mGalleryFolder;

    private ImageReader mImageReader;

    private int count = 0;
    private static int MAX_CAPTURE = 0;

    public int STATE_BTN_ONE = 1;
    public int STATE_BTN_THREE = 2;
    public int STATE_BTN_SIX = 3;

    private int btn_pressed = 1;
    private List<Image> mImageList = new ArrayList<>();

    private List<byte[]> bytelist = new ArrayList<>();


    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private long previoustime = 0;

    public final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        private int  frames = 0;
        private long initialTime = SystemClock.elapsedRealtimeNanos();
        @Override
        public void onImageAvailable(ImageReader reader) {
            count++;
            //Log.e("count In Image", count + "");

            Image image = reader.acquireNextImage();
            //Log.d("onImageAvailable", ": IMAGE AVAILABLE " + DateUtils.formatDateTime(MainActivity.this, image.getTimestamp() , DateUtils.FORMAT_SHOW_TIME) );

            ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
            bytelist.add(bytes);
            image.close();

            frames++;
            if ((frames % MAX_CAPTURE) == 0) {
                long currentTime = SystemClock.elapsedRealtimeNanos();
                long fps = Math.round(frames * 1e9 / (currentTime - initialTime));
                Log.d("Image", "frame# : " + frames + ", approximately " + fps + " fps");
                frames = 0;
                initialTime = SystemClock.elapsedRealtimeNanos();
            }
            if (count >= MAX_CAPTURE) {
                //capture completed,now save
                Log.e("Background Saving", "Started" + bytelist.size());
                mBackgroundHandler.post(new ImageSaver());
                deleteCache(MainActivity.this);
            }
        }

    };

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
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            process(result);
        }

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW:
                    //do nothing
                    break;
                case STATE_WAIT_LOCK:
                    //lock focus
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED) {
                        Log.e("Camera focus locked", "");
                        try {
                            // captuteStillImage();

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        unlockFocus();
                    }
                    break;
            }
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
        }

        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);

        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createImageGallery();
        //finiding views
        mTextureView = (TextureView) findViewById(R.id.textureView);
        tv_count = (TextView) findViewById(R.id.tv_count);

    }

    @Override
    protected void onResume() {
        super.onResume();
        openBackgroundThread();
        if (mTextureView.isAvailable()) {
            //so set up the camera
            setupCamera(mTextureView.getWidth(), mTextureView.getHeight());
            //open the camera
            openCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        closeBackgroundThread();
        super.onPause();
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

                //set up captured image size
                Size largestImageSize = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new Comparator<Size>() {

                            @Override
                            public int compare(Size lhs, Size rhs) {
                                return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getWidth() * rhs.getHeight());
                            }
                        }

                );

                //get the closest minimum size of the camera supported and set it to our textureview
                mPreviewSize = getPreferredPreviewSize(map.getOutputSizes(SurfaceTexture.class), width, height);

                mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.JPEG, 2);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);


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
            cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);

        } catch (CameraAccessException ex) {
            ex.printStackTrace();
        }
    }

    private void closeCamera() {
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }

        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }

        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
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
            List surfaces = new ArrayList<>();

            surfaces.add(previewSurface);
            mPreviewCaptureRequestBuilder.addTarget(previewSurface);

            Surface readerSurface = mImageReader.getSurface();
            surfaces.add(readerSurface);

            //now create capture session
            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
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
                        mCameraCaptureSession.setRepeatingBurst(Arrays.asList(mPreviewCaptureRequest), mSessionCaptureCallback, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e("OnCameraSession: ", "failed");
                }
            }, null);

        } catch (CameraAccessException ex) {
            ex.printStackTrace();
        }
    }

    protected void openBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera2 Background thread");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    protected void closeBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    public void take_picture(View view) {
        btn_pressed = STATE_BTN_ONE;
        MAX_CAPTURE = 1;

        //lockFocus();
        captuteStillImage(1);
    }


    private void lockFocus() {
        try {
            mState = STATE_WAIT_LOCK;
            mPreviewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            mCameraCaptureSession.capture(mPreviewCaptureRequestBuilder.build(), mSessionCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void unlockFocus() {
        try {
            mState = STATE_PREVIEW;
            mPreviewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
            mCameraCaptureSession.capture(mPreviewCaptureRequestBuilder.build(), mSessionCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    File createImageFile() throws IOException {

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "IMAGE_" + timeStamp + "_";

        File image = File.createTempFile(imageFileName, ".jpg", mGalleryFolder);
        mImageFileLocation = image.getAbsolutePath();

        return image;

    }

    public void take_picture_three(View view) {
        btn_pressed = STATE_BTN_THREE;
        MAX_CAPTURE = 3;
        //lockFocus();
        captuteStillImage(3);

    }

    public void take_picture_six(View view) {
        btn_pressed = STATE_BTN_SIX;
        //lockFocus();
        captuteStillImage(6);

    }

    public void open_gallery(View view) {
        openGallery();
    }

    //save image in background
    private class ImageSaver implements Runnable {
        //private final Image mImage;

        private ImageSaver(Image image) {
            //mImage = image;
        }

        private ImageSaver() {
            // mImage = image;
        }

        @Override
        public void run() {
            FileOutputStream fileOutputStream = null;

            for (byte[] byteItem : bytelist) {
                try {
                    mImageFile = createImageFile();
                    fileOutputStream = new FileOutputStream(mImageFile);
                    fileOutputStream.write(byteItem);
                    galleryAddPic();
                    Log.e("Saving Complete", "successfull,listSize: " + bytelist.size());
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e("Saving Complete", "unsuccessfull,listSize: "+ bytelist.size());
                } finally {
                    if (fileOutputStream != null) {
                        try {
                            fileOutputStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                }
            }

        }
    }

    private int show_capture_count = 0;

    private void captuteStillImage(int image_count) {
        try {
            count = 0;
            MAX_CAPTURE = image_count;
            show_capture_count = 0;
            bytelist.clear();
            CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            Range<Integer> bestFPSRange = new Range<>(MAX_CAPTURE,MAX_CAPTURE);
            //captureBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, bestFPSRange);
            //captureBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range.create(0,6));
            captureBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, getRange());


            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    //only showing the counts in screen
                    show_capture_count++;
                    Log.e("count In Session", show_capture_count + "");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tv_count.setText(show_capture_count + "");

                        }
                    });

                    Log.e("Image Capture", "Successfully");
                }
            };
            List<CaptureRequest> captureList = new ArrayList<CaptureRequest>();
            captureBuilder.addTarget(mImageReader.getSurface());
            for (int i = 0; i < MAX_CAPTURE; i++) {
                captureList.add(captureBuilder.build());
            }
            //mCameraCaptureSession.stopRepeating();
            mCameraCaptureSession.captureBurst(captureList, captureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void createImageGallery() {
        //File storageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        mGalleryFolder = new File(Environment.getExternalStorageDirectory(), GALLERY_LOCATION);
        if (!mGalleryFolder.exists()) {
            mGalleryFolder.mkdirs();
        }

    }

    public static void deleteCache(Context context) {
        try {
            File dir = context.getCacheDir();
            deleteDir(dir);
        } catch (Exception e) {
        }
    }

    public static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        } else {
            return false;
        }
    }

    private void galleryAddPic() {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File f = new File(mImageFileLocation);
        Uri contentUri = Uri.fromFile(f);
        mediaScanIntent.setData(contentUri);
        this.sendBroadcast(mediaScanIntent);
    }

    private void openGallery(){
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(
                "content://media/internal/images/ "));
        startActivity(intent);
    }

    private Range<Integer> getRange() {
        //get the camera manager
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics chars = null;
        Range<Integer> result = null;
        Range<Integer>[] ranges = null;
        try {
            chars = cameraManager.getCameraCharacteristics(mCameraId);
            ranges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);


            for (Range<Integer> range : ranges) {
                int upper = range.getUpper();

                // 10 - min range upper for my needs
                if (upper <= MAX_CAPTURE) {
                    if (result == null || upper > result.getUpper().intValue()) {
                        result = range;
                    }
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }


        if (result == null) {
            //result = ranges[0];
            result = new Range<>(MAX_CAPTURE,MAX_CAPTURE);
        }
        Log.e("Range",result+"");
        return result;
    }
}
