package com.example.facereco;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.widget.UVCCameraTextureView;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

// Activité principale de l'application. Elle gère les affichages et les liens entre
// les différentes fonctions
public final class MainActivity extends Activity implements CameraDialog.CameraDialogParent {

    private static final String TAG = "OCVSample::Activity";

    // for thread pool
    private static final int CORE_POOL_SIZE = 1;		// initial/minimum threads
    private static final int MAX_POOL_SIZE = 4;			// maximum threads
    private static final int KEEP_ALIVE_TIME = 10;		// time periods while keep the idle thread
    protected static final ThreadPoolExecutor EXECUTER
            = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME,
            TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    // for accessing USB and USB camera
    private USBMonitor mUSBMonitor;
    private UVCCamera mUVCCamera;
    private UVCCameraTextureView mUVCCameraView;
    // for open&start / stop&close camera preview
    private ImageButton mCameraButton;
    private Surface mPreviewSurface;
    // Pour l'affichage de l'image et du rectangle du visage
    private ImageView mImageView_video;
    private ImageView mImageView_detect;

// Taille des images pour chaque algorithme
    private int PIXEL_WIDTH_AGEGENDER = 224;
    private int PIXEL_WIDTH_EMOTION = 96;

    Context context = this;

// Création des classifiers, commenter, décommenter en fonctions des classifiers que l'on souhaite tester
    private AgeGenderClassifier ageGenderClassifier;
    private Emotions emotions;
    private FaceDetector faceDetector;
    @Override
    // Création de l'application
    protected void onCreate(final Bundle savedInstanceState) {
        // Chargement des différents classifiers, commenter ou décommenter en fonction du besoin

        try{
            ageGenderClassifier = new AgeGenderClassifier(this);
        }catch (IOException e) {
            Log.e(TAG, "Failed to load age_gender classifier");
        }

        try{
            emotions = Emotions.classifier(this.getAssets(), "emotion_96.tflite");
        }catch (IOException e) {
            Log.e(TAG, "Failed to load emotion classifier");
        }


        // Chargement du detecteur de visage, issue de la lib de google
        faceDetector = new FaceDetector.Builder(context)
                .setTrackingEnabled(true)
                .setMode(FaceDetector.FAST_MODE)
                .setLandmarkType(FaceDetector.NO_LANDMARKS)
                .setClassificationType(FaceDetector.NO_CLASSIFICATIONS)
                .build();

        // Création des différents objets de l'application qui doivent s'afficher, notament le boutton
        // pour choisir la caméra et les vues
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mCameraButton = (ImageButton)findViewById(R.id.camera_button);
        mCameraButton.setOnClickListener(mOnClickListener);

        mImageView_video = (ImageView)findViewById(R.id.imageview_video);
        mImageView_detect = (ImageView)findViewById(R.id.imageview_detection);
//        mImageView_detect.setAlpha(0.0f);

        mUVCCameraView = (UVCCameraTextureView)findViewById(R.id.UVCCameraTextureView1);
        mUVCCameraView.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / (float)UVCCamera.DEFAULT_PREVIEW_HEIGHT);

        mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);

    }

    // Options pour si l'application s'éteint, crash, ou se rallume

    @Override
    public void onResume() {
        super.onResume();
        mUSBMonitor.register();
        if (mUVCCamera != null)
            mUVCCamera.startPreview();
    }

    @Override
    public void onPause() {
        if (mUVCCamera != null)
            mUVCCamera.stopPreview();
        mUSBMonitor.unregister();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (mUVCCamera != null) {
            mUVCCamera.destroy();
            mUVCCamera = null;
        }
        if (mUSBMonitor != null) {
            mUSBMonitor.destroy();
            mUSBMonitor = null;
        }
        mUVCCameraView = null;
        mCameraButton = null;

//        ageGender.close();
        super.onDestroy();
    }

    // Je sais pas
    private final OnClickListener mOnClickListener = new OnClickListener() {
        @Override
        public void onClick(final View view) {
            if (mUVCCamera == null)
                CameraDialog.showDialog(MainActivity.this);
            else {
                mUVCCamera.destroy();
                mUVCCamera = null;
            }
        }
    };

    // Affichage d'un text si la caméra est connectée
    private final OnDeviceConnectListener mOnDeviceConnectListener = new OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
            Toast.makeText(MainActivity.this, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
        }

        // Récupération de la vidéo de la webcam et affichage
        @Override
        public void onConnect(final UsbDevice device, final UsbControlBlock ctrlBlock, final boolean createNew) {
            if (mUVCCamera != null)
                mUVCCamera.destroy();
            mUVCCamera = new UVCCamera();
            EXECUTER.execute(new Runnable() {
                @Override
                public void run() {
                    mUVCCamera.open(ctrlBlock);
                    mUVCCamera.setPreviewTexture(mUVCCameraView.getSurfaceTexture());
                    if (mPreviewSurface != null) {
                        mPreviewSurface.release();
                        mPreviewSurface = null;
                    }
                    try {
                        mUVCCamera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG);
                    } catch (final IllegalArgumentException e) {
                        // fallback to YUV mode
                        try {
                            mUVCCamera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.DEFAULT_PREVIEW_MODE);
                        } catch (final IllegalArgumentException e1) {
                            mUVCCamera.destroy();
                            mUVCCamera = null;
                        }
                    }
                    if (mUVCCamera != null) {
                        final SurfaceTexture st = mUVCCameraView.getSurfaceTexture();
                        if (st != null)
                            mPreviewSurface = new Surface(st);
                        mUVCCamera.setPreviewDisplay(mPreviewSurface);
                        mUVCCamera.setFrameCallback(mIFrameCallback, UVCCamera.PIXEL_FORMAT_RGB565/*UVCCamera.PIXEL_FORMAT_NV21*/);

                        mUVCCamera.startPreview();

                    }
                }
            });
        }

        // Lorsque la caméra n'est plus connectée, on arrète d'afficher l'image
        @Override
        public void onDisconnect(final UsbDevice device, final UsbControlBlock ctrlBlock) {
            // XXX you should check whether the comming device equal to camera device that currently using
            if (mUVCCamera != null) {
                mUVCCamera.close();
                if (mPreviewSurface != null) {
                    mPreviewSurface.release();
                    mPreviewSurface = null;
                }
            }
        }

        @Override
        public void onDettach(final UsbDevice device) {
            Toast.makeText(MainActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCancel() {
        }
    };

    // Fonction qui détecte les visages et appelle les différents algos de reconnaissance
    public Bitmap detect_face(Bitmap bitmap){


        String detection_case = "age_gender_emotion"; // "age_gender_emotion" ou "face_reco"
        // Création d'un bitmap pour afficher les détections
        Bitmap imageBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        // Le canvas permet de dessiner sur le bitmap
        Canvas tempCanvas = new Canvas(imageBitmap);
        tempCanvas.drawColor(Color.TRANSPARENT);
        Paint paint1 = new Paint();
        paint1.setColor(Color.GREEN);
        paint1.setStyle(Paint.Style.STROKE);
        paint1.setStrokeWidth(2.0f);
        Paint paint2 = new Paint();
        paint2.setColor(Color.RED);
        paint2.setStyle(Paint.Style.FILL);

        // Detection des visages
        Frame frame = new Frame.Builder().setBitmap(bitmap).build();
        SparseArray<Face> faces = faceDetector.detect(frame);
        for (int i = 0; i < faces.size(); ++i) {
            // Pour chaque visage on créé un carré autour
            Face face = faces.valueAt(i);
            int[] new_face = ImageProcess.smallSquare(face);
            int l = new_face[0];
            int x = new_face[1];
            int y = new_face[2];
            String text = "";

            // Si le carré est au moins égale la taille minimale de l'image
            if ((x + l) <= bitmap.getWidth() && (y + l) <= bitmap.getHeight() && x > 0 && y > 0 && l >= PIXEL_WIDTH_EMOTION) {
                Bitmap resizedBitmap = ImageProcess.getResizedBitmap(Bitmap.createBitmap(bitmap, x, y, l, l), PIXEL_WIDTH_EMOTION, PIXEL_WIDTH_EMOTION);
                Bitmap resizedBitmap2 = ImageProcess.getResizedBitmap(Bitmap.createBitmap(bitmap, x, y, l, l), PIXEL_WIDTH_AGEGENDER, PIXEL_WIDTH_AGEGENDER);
                text += ageGenderClassifier.classifyFrame(resizedBitmap2);
                text += " ";
                text += emotions.recognizeImage(resizedBitmap);
                System.out.println(text);
                tempCanvas.drawText(text, x, y, paint2);

            }
            
            // dessin du rectangle
            tempCanvas.drawRect(new Rect(
                            x,
                            y,
                            x + l,
                            y + l),
                    paint1);


        }

        return imageBitmap;
    }

    @Override
    public USBMonitor getUSBMonitor() {
        return mUSBMonitor;
    }

    /////////////////////////////////////////////////////////////////////////////////////

    // if you need frame data as byte array on Java side, you can use this callback method with UVCCamera#setFrameCallback
    // if you need to create Bitmap in IFrameCallback, please refer following snippet.
    final Bitmap bitmap = Bitmap.createBitmap(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, Bitmap.Config.RGB_565);

    // Fonction de callback appelée pour récupérer image par image et faire le traitement dessus
    private final IFrameCallback mIFrameCallback = new IFrameCallback() {

        @Override
        public void onFrame(final ByteBuffer frame) {
//            frame.clear();
            synchronized (bitmap) {
                bitmap.copyPixelsFromBuffer(frame);
            }

            mImageView_video.post(mUpdateImageTask);
            mUpdateImageReco.run();
        }
    };

    // Affiche image par image dans mImageView_video
    private final Runnable mUpdateImageTask = new Runnable() {
        @Override
        public void run() {
            synchronized (bitmap) {
                mImageView_video.setImageBitmap(bitmap);
//                Bitmap final_bitmap = detect_face(bitmap);
//
//                mImageView_detect.setImageBitmap(final_bitmap);
            }
        }
    };
    // Récupère l'image d'ImageView_vidéo, et fait les traitements pour faire un nouvel affichage
    // en transparence avec le carré du visage et les detections
    private final Runnable mUpdateImageReco = new Runnable() {
        @Override
        public void run() {
            if (mImageView_video.getDrawable() != null) {
                final Bitmap b = ((BitmapDrawable) mImageView_video.getDrawable()).getBitmap();
                mImageView_detect.setImageBitmap(detect_face(b));
            }
            else{
                System.out.print("ERROR !!!!!");
            }

        }
    };
}

