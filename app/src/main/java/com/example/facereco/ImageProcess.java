package com.example.facereco;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import com.google.android.gms.vision.face.Face;

public class ImageProcess {

    // La detection faciale renvoie un rectangle et cette fonction permet de le transformer en carré
    public static int[] smallSquare(Face face){
        int[] new_face = new int[3];
        PointF position = face.getPosition();
        new_face[0] = (int)(Math.min(face.getHeight(), face.getWidth()));
        if (face.getHeight() > face.getWidth()){
            new_face[2] = (int) (position.y + (face.getHeight() - new_face[0])/2);
            new_face[1] = (int) (position.x);
        }
        else{
            new_face[2] = (int) (position.y);
            new_face[1] = (int) (position.x + (face.getWidth() - new_face[0]));
        }

        return new_face;
    }

    // Change la taille du bitmap (de l'image)
    public static Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        // "RECREATE" THE NEW BITMAP
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
        bm.recycle();
        return resizedBitmap;
    }

}
