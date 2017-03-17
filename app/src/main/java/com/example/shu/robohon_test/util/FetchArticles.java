package com.example.shu.robohon_test.util;

import android.os.AsyncTask;
import android.util.Log;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

/**
 * 記事取得タスク
 */

public class FetchArticles extends AsyncTask<Void, Void, JSONArray> {
    public static final String TAG = FetchArticles.class.getSimpleName();

    public interface Callback {
        void onFinished(JSONArray result);
    }

    protected Callback callback;

    public FetchArticles(Callback callback) {
        this.callback = callback;
    }

    @Override
    protected JSONArray doInBackground(Void... params) {
        return getArticles();
    }

    @Override
    protected void onPostExecute(JSONArray result) {
        callback.onFinished(result);
    }

    private JSONArray getArticles() {
        JSONArray result = null;
        String apiUrl = "https://api.yokotv.com/api/Articles";
        Date today = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN);
        String todayStr = dateFormat.format(today);
        String params = "?filter[where][startDate][lte]="+todayStr+"&filter[where][endDate][gte]="+todayStr+"&filter[where][content][neq]=&filter[limit]=20&filter[include]=images";

        try {
            URL url = new URL(apiUrl+params);

            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

            connection.setRequestMethod("GET");
            connection.connect();
            int resCode = connection.getResponseCode();

            if (resCode == HttpsURLConnection.HTTP_OK) {
                Log.v(TAG, "Http OK");
                InputStream is = connection.getInputStream();
                String res = IOUtils.toString(is, StandardCharsets.UTF_8);
                result = new JSONArray(res);
                is.close();
            }
        } catch(MalformedURLException e) {
            Log.e(TAG, "MalformedURLException:" + e.getMessage());
        } catch(IOException e) {
            Log.e(TAG, "IOException:" + e.getMessage());
        } catch(JSONException e) {
            Log.e(TAG, "JSONException:" + e.getMessage());
        }
        return result;
    }
}
