package com.example.facereco;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

public class Emotions {

    private final Interpreter interpreter;
    private int SIZE_IMAGE = 96;

    private Emotions(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    public static Emotions classifier(AssetManager assetManager, String modelPath) throws IOException {
        ByteBuffer byteBuffer = loadModelFile(assetManager, modelPath);
        Interpreter interpreter = new Interpreter(byteBuffer);
        return new Emotions(interpreter);
    }

    private static ByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public String recognizeImage(Bitmap bitmap) {
        ByteBuffer byteBuffer = convertBitmapToByteBuffer(bitmap);

        float[][] emotion = new float[1][7];

        interpreter.run(byteBuffer, emotion);
//        System.out.print(emotion[0][0]);
//        System.out.print(" ");
//        System.out.print(emotion[0][1]);
//        System.out.print(" ");
//        System.out.print(emotion[0][2]);
//        System.out.print(" ");
//        System.out.print(emotion[0][3]);
//        System.out.print(" ");
//        System.out.print(emotion[0][4]);
//        System.out.print(" ");
//        System.out.print(emotion[0][5]);
//        System.out.print(" ");
//        System.out.print(emotion[0][6]);
//        System.out.print(" ");
        String result = convertToResults(emotion[0]);
//        System.out.println(result);
        return result;
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4*1*SIZE_IMAGE*SIZE_IMAGE*3);
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] pixels = new int[SIZE_IMAGE * SIZE_IMAGE];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
//        for (int pixel : pixels) {
//            float rChannel = (pixel >> 16) & 0xFF;
//            float gChannel = (pixel >> 8) & 0xFF;
//            float bChannel = (pixel) & 0xFF;
//            float pixelValue = (rChannel + gChannel + bChannel) / 3 / 255.f;
////            float pixelValue = (rChannel + gChannel + bChannel) / 3 ;
//            byteBuffer.putFloat(pixelValue);
//        }
        int pixel = 0;
        for(int i=0; i < SIZE_IMAGE; i++){
            for(int j=0; j < SIZE_IMAGE;j++){
                final int val = pixels[pixel++];
//                float gray_value = (float) (0.21*((val >> 16) & 0xFF) + 0.72*((val >> 8) & 0xFF) + 0.07*(val  & 0xFF))/255;
//                byteBuffer.putFloat(gray_value);
//                byteBuffer.putFloat(gray_value);
//                byteBuffer.putFloat(gray_value);
                byteBuffer.putFloat((float) ((val >> 16) & 0xFF));
                byteBuffer.putFloat((float)((val >> 8)& 0xFF));
                byteBuffer.putFloat((float) (val & 0xFF));


            }
        }

        return byteBuffer;
    }
    private String convertToResults(float[] emotion){
//        Object gender = cnnOutputs.get(0);
//        float[] age = labelProbArray[1];

        String[] emotion_matrix = {"angry","disgust","fear","happy","sad","surprised", "neutral"};
        int index = argMax(emotion);

//        result += Double.toString((dotProduct(age, new_matrix)*4.76));
        String result = emotion_matrix[index];

        return result;
    }

    private float dotProduct(float[] matrix1, float[][] matrix2){
        float sum=0;
        for (int i=0; i<matrix1.length; i++){
            sum += matrix1[i]*matrix2[i][0];
        }
        return sum;
    }

    private int argMax(float[] list){
        int index = -1;
        float best_result = 0.0f;
        for(int i=0; i<list.length; i++){
            if(list[i]>best_result){
                index = i;
                best_result = list[i];
            }
        }
        return index;
    }
}
