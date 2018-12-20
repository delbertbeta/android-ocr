/*
 * Copyright (C) 2010 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.zl.tesseract.scanner.decode;

import android.app.ApplicationErrorReport;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.zl.tesseract.R;
import com.zl.tesseract.scanner.ScannerActivity;
import com.zl.tesseract.scanner.MyApplication;
import com.zl.tesseract.scanner.tess.TessEngine;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

final class DecodeHandler extends Handler {

    private final ScannerActivity mActivity;
    private final MultiFormatReader mMultiFormatReader;
    private final Map<DecodeHintType, Object> mHints;
    private byte[] mRotatedData;

    DecodeHandler(ScannerActivity activity) {
        this.mActivity = activity;
        mMultiFormatReader = new MultiFormatReader();
        mHints = new Hashtable<>();
        mHints.put(DecodeHintType.CHARACTER_SET, "utf-8");
        mHints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        Collection<BarcodeFormat> barcodeFormats = new ArrayList<>();
        barcodeFormats.add(BarcodeFormat.CODE_39);
        barcodeFormats.add(BarcodeFormat.CODE_128); // 快递单常用格式39,128
        barcodeFormats.add(BarcodeFormat.QR_CODE); //扫描格式自行添加
        mHints.put(DecodeHintType.POSSIBLE_FORMATS, barcodeFormats);
    }

    @Override
    public void handleMessage(Message message) {
        switch (message.what) {
            case R.id.decode:
                try {
                    decode((byte[]) message.obj, message.arg1, message.arg2);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case R.id.quit:
                Looper looper = Looper.myLooper();
                if (null != looper) {
                    looper.quit();
                }
                break;
        }
    }

    /**
     * Decode the data within the viewfinder rectangle, and time how long it took. For efficiency, reuse the same reader
     * objects from one decode to the next.
     *
     * @param data   The YUV preview frame.
     * @param width  The width of the preview frame.
     * @param height The height of the preview frame.
     */
    private void decode(byte[] data, int width, int height) throws IOException {

        // Run all things remotely.
//
//        File fileFolder = MyApplication.sAppContext.getCacheDir();
//        if (!fileFolder.exists()) {
//            fileFolder.mkdir();
//        }
//        final File file = new File(MyApplication.sAppContext.getCacheDir(), "image.jpg");
//        file.createNewFile();
//
////        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
////        ByteBuffer buffer = ByteBuffer.wrap(data);
////        buffer.rewind();
////        bitmap.copyPixelsFromBuffer(buffer);
//        YuvImage yuv = new YuvImage(data, ImageFormat.NV21, width, height, null);
//
//        FileOutputStream outputStream = new FileOutputStream(file);
//
//        yuv.compressToJpeg(new Rect(0, 0, width, height), 100, outputStream);
//
////        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
//        outputStream.close();
//
//        RequestBody fileBody = RequestBody.create(MediaType.get("image/jpeg"), file);
//
//        final Rect rect = mActivity.getCropRect();
//
//        MultipartBody body = new MultipartBody.Builder()
//                .setType(MultipartBody.FORM)
//                .addFormDataPart("width", String.valueOf(width))
//                .addFormDataPart("height", String.valueOf(height))
//                .addFormDataPart("left", String.valueOf(rect.left))
//                .addFormDataPart("top", String.valueOf(rect.top))
//                .addFormDataPart("canvasHeight", String.valueOf(rect.height()))
//                .addFormDataPart("canvasWidth", String.valueOf(rect.width()))
//                .addFormDataPart("file", "audio.aac", fileBody)
//                .build();
//
//        Request request = new Request.Builder()
//                .url("http://192.168.1.133:8080/decode")
//                .post(body)
//                .build();
//
//        OkHttpClient client = new OkHttpClient();
//
//        client.newCall(request).enqueue(new okhttp3.Callback() {
//            @Override
//            public void onFailure(Call call, IOException e) {
//                e.printStackTrace();
//            }
//
//            @Override
//            public void onResponse(Call call, Response response) throws IOException {
//                try {
//                    JSONObject json = new JSONObject(response.body().string());
//                    Result rawResult = new Result(json.getString("text"), null, null, null);
//
//                    byte[] newImage = Base64.decode(json.getString("image"), Base64.DEFAULT);
////
////                    int[] imageArray = new int[rect.width() * rect.height()];
////
////                    for (int i = 0; i < newImage.length; i++) {
////                        imageArray[i] = newImage[i];
////                    }
////
////                    Bitmap bitmap = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888);
////                    bitmap.setPixels(imageArray, 0, rect.width(), 0, 0, rect.width(), rect.height());
//
//                    Bitmap bitmap = BitmapFactory.decodeByteArray(newImage, 0, newImage.length);
//
//                    rawResult.setBitmap(bitmap);
//                    Message message = Message.obtain(mActivity.getCaptureActivityHandler(), R.id.decode_succeeded, rawResult);
//                    message.sendToTarget();
//                } catch (JSONException e) {
//                    e.printStackTrace();
//                }
//
//            }
//        });
//        return;

        ConnectivityManager connectivityManager;//用于判断是否有网络
        connectivityManager = (ConnectivityManager)MyApplication.sAppContext.getSystemService(Context.CONNECTIVITY_SERVICE);//获取当前网络的连接服务
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();
        BatteryManager batteryManager = (BatteryManager)MyApplication.sAppContext.getSystemService(Context.BATTERY_SERVICE);


        // Run all things locally.
        if (MyApplication.batteryState == 0 || info == null) {
            if (null == mRotatedData) {
                mRotatedData = new byte[width * height];
            } else {
                if (mRotatedData.length < width * height) {
                    mRotatedData = new byte[width * height];
                }
            }
            Arrays.fill(mRotatedData, (byte) 0);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (x + y * width >= data.length) {
                        break;
                    }
                    mRotatedData[x * height + height - y - 1] = data[x + y * width];
                }
            }
            int tmp = width; // Here we are swapping, that's the difference to #11
            width = height;
            height = tmp;

            Result rawResult = null;
            try {
                Rect rect = mActivity.getCropRect();
                if (rect == null) {
                    return;
                }

                PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(mRotatedData, width, height, rect.left, rect.top, rect.width(), rect.height(), false);

                if (mActivity.isQRCode()) {
                    BinaryBitmap bitmap1 = new BinaryBitmap(new HybridBinarizer(source));
                    rawResult = mMultiFormatReader.decode(bitmap1, mHints);
                } else {
                    TessEngine tessEngine = TessEngine.Generate(MyApplication.sAppContext);
                    Bitmap bitmap = source.renderCroppedGreyscaleBitmap();
                    String result = tessEngine.detectText(bitmap);
                    result = "本次计算在本地完成：\n" + result;
                    if (info == null) {
                        result = "由于您未连接网络，" + result;
                    }
                    rawResult = new Result(result, null, null, null);
                    rawResult.setBitmap(bitmap);
                }

            } catch (Exception ignored) {
            } finally {
                mMultiFormatReader.reset();
            }

            if (rawResult != null) {
                Message message = Message.obtain(mActivity.getCaptureActivityHandler(), R.id.decode_succeeded, rawResult);
                message.sendToTarget();
            } else {
                Message message = Message.obtain(mActivity.getCaptureActivityHandler(), R.id.decode_failed, new Result("", null, null, null));
                message.sendToTarget();
            }
        } else {


            // TessEngine Run Remotely.
            if (null == mRotatedData) {
                mRotatedData = new byte[width * height];
            } else {
                if (mRotatedData.length < width * height) {
                    mRotatedData = new byte[width * height];
                }
            }
            Arrays.fill(mRotatedData, (byte) 0);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (x + y * width >= data.length) {
                        break;
                    }
                    mRotatedData[x * height + height - y - 1] = data[x + y * width];
                }
            }
            int tmp = width; // Here we are swapping, that's the difference to #11
            width = height;
            height = tmp;

            Rect rect = mActivity.getCropRect();
            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(mRotatedData, width, height, rect.left, rect.top, rect.width(), rect.height(), false);
            final Bitmap bitmap = source.renderCroppedGreyscaleBitmap();

            File fileFolder = MyApplication.sAppContext.getCacheDir();
            if (!fileFolder.exists()) {
                fileFolder.mkdir();
            }
            final File file = new File(MyApplication.sAppContext.getCacheDir(), "image.jpg");
            file.createNewFile();

            FileOutputStream outputStream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            outputStream.close();

            RequestBody fileBody = RequestBody.create(MediaType.get("image/jpeg"), file);

            MultipartBody body = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", "audio.aac", fileBody)
                    .build();

            Request request = new Request.Builder()
                    .url("http://192.168.1.133:8080/ocr")
                    .post(body)
                    .build();

            OkHttpClient client = new OkHttpClient();

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    e.printStackTrace();
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        Result rawResult = new Result("本次计算在服务器完成：\n" + json.getString("text"), null, null, null);

                        rawResult.setBitmap(bitmap);
                        Message message = Message.obtain(mActivity.getCaptureActivityHandler(), R.id.decode_succeeded, rawResult);
                        message.sendToTarget();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                }
            });
        }
    }
}
