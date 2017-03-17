package com.example.shu.robohon_test.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

/**
 * 画像取得タスク
 */

public class FetchImage extends AsyncTask<Void, Void, Bitmap> {
    public static final String TAG = FetchImage.class.getSimpleName();

    public interface Callback {
        void onFinished(Bitmap bitmap);
    }

    protected Callback callback;
    protected String url;

    public FetchImage(String url, Callback callback) {
        this.callback = callback;
        this.url = url;
    }

    @Override
    protected Bitmap doInBackground(Void... params) {
        return getImage(url);
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        callback.onFinished(bitmap);
    }

    private Bitmap getImage(String urlString) {
        Bitmap result = null;

        try {
            URL url = new URL(urlString);

            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

            connection.setRequestMethod("GET");
            connection.connect();
            int resCode = connection.getResponseCode();

            if (resCode == HttpsURLConnection.HTTP_OK) {
                Log.v(TAG, "Http OK");
                InputStream is = connection.getInputStream();
                result = BitmapFactory.decodeStream(is);
                is.close();
            }
        } catch(MalformedURLException e) {
            Log.e(TAG, "MalformedURLException:" + e.getMessage());
        } catch(IOException e) {
            Log.e(TAG, "IOException:" + e.getMessage());
        }

        return result;
    }
}
