package com.example.shu.robohon_test;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.widget.ImageView;

import java.util.List;

import com.example.shu.robohon_test.customize.ScenarioDefinitions;
import com.example.shu.robohon_test.util.FetchArticles;
import com.example.shu.robohon_test.util.FetchImage;
import com.example.shu.robohon_test.util.VoiceUIManagerUtil;
import com.example.shu.robohon_test.util.VoiceUIVariableUtil.VoiceUIVariableListHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import jp.co.sharp.android.rb.projectormanager.ProjectorManagerServiceUtil;
import jp.co.sharp.android.voiceui.VoiceUIManager;
import jp.co.sharp.android.voiceui.VoiceUIVariable;

public class MainActivity extends Activity implements MainActivityVoiceUIListener.MainActivityScenarioCallback {
    public static final String TAG = MainActivity.class.getSimpleName();

    /**
     * 音声UI制御.
     */
    private VoiceUIManager mVoiceUIManager = null;
    /**
     * 音声UIイベントリスナー.
     */
    private MainActivityVoiceUIListener mMainActivityVoiceUIListener = null;
    /**
     * 音声UIの再起動イベント検知.
     */
    private VoiceUIStartReceiver mVoiceUIStartReceiver = null;
    /**
     * ホームボタンイベント検知.
     */
    private HomeEventReceiver mHomeEventReceiver;
    /**
     * プロジェクター状態変化イベント検知.
     */
    private ProjectorEventReceiver mProjectorEventReceiver;
    /**
     * プロジェクタ照射中のWakelock.
     */
    private android.os.PowerManager.WakeLock mWakelock;
    /**
     * 排他制御用.
     */
    private final Object mLock = new Object();
    /**
     * プロジェクタ照射状態.
     */
    private boolean isProjected = false;

    private int currentIndex;
    private JSONArray articles = null;
    private ImageView imageView = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate()");
        setContentView(R.layout.activity_main);

        // タイトルバー設定.
        setupTitleBar();

        // ホームボタンの検知登録.
        mHomeEventReceiver = new HomeEventReceiver();
        IntentFilter filterHome = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(mHomeEventReceiver, filterHome);

        // VoiceUI再起動の検知登録.
        mVoiceUIStartReceiver = new VoiceUIStartReceiver();
        IntentFilter filter = new IntentFilter(VoiceUIManager.ACTION_VOICEUI_SERVICE_STARTED);
        registerReceiver(mVoiceUIStartReceiver, filter);

        // プロジェクタイベントの検知登録(プロジェクター利用時のみ).
        setProjectorEventReceiver();

        // 発話ボタンの実装.
//        Button Button = (Button) findViewById(R.id.accost);
//        Button.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                execAsync();
//            }
//        });

//        Button projButton = (Button) findViewById(R.id.projector);
//        projButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if(!isProjected) {
//                    startService(getIntentForProjector());
//                }
//            }
//        });

        currentIndex = 0;
        imageView = (ImageView) findViewById(R.id.imageView);

        execAsync();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.v(TAG, "onResume()");

        //VoiceUIManagerのインスタンス取得.
        if (mVoiceUIManager == null) {
            mVoiceUIManager = VoiceUIManager.getService(getApplicationContext());
        }
        //MainActivityVoiceUIListener生成.
        if (mMainActivityVoiceUIListener == null) {
            mMainActivityVoiceUIListener = new MainActivityVoiceUIListener(this);
        }
        //VoiceUIListenerの登録.
        VoiceUIManagerUtil.registerVoiceUIListener(mVoiceUIManager, mMainActivityVoiceUIListener);

        //Scene有効化.
        VoiceUIManagerUtil.enableScene(mVoiceUIManager, ScenarioDefinitions.SCENE_COMMON);
        VoiceUIManagerUtil.enableScene(mVoiceUIManager, ScenarioDefinitions.SCENE01);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.v(TAG, "onPause()");

        //バックに回ったら発話を中止する.
        VoiceUIManagerUtil.stopSpeech();

        //VoiceUIListenerの解除.
        VoiceUIManagerUtil.unregisterVoiceUIListener(mVoiceUIManager, mMainActivityVoiceUIListener);

        //Scene無効化.
        VoiceUIManagerUtil.disableScene(mVoiceUIManager, ScenarioDefinitions.SCENE_COMMON);
        VoiceUIManagerUtil.disableScene(mVoiceUIManager, ScenarioDefinitions.SCENE01);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy()");

        // ホームボタンの検知破棄.
        this.unregisterReceiver(mHomeEventReceiver);

        // VoiceUI再起動の検知破棄.
        this.unregisterReceiver(mVoiceUIStartReceiver);

        // プロジェクタイベントの検知破棄(プロジェクター利用時のみ).
        this.unregisterReceiver(mProjectorEventReceiver);

        // インスタンスのごみ掃除.
        mVoiceUIManager = null;
        mMainActivityVoiceUIListener = null;
        mProjectorEventReceiver = null;
    }

    /**
     * VoiceUIListenerクラスからのコールバックを実装する.
     */
    @Override
    public void onExecCommand(String command, List<VoiceUIVariable> variables) {
        Log.v(TAG, "onExecCommand() : " + command);
        switch (command) {
            case ScenarioDefinitions.FUNC_END_APP:
                finish();
                break;
            case ScenarioDefinitions.FUNC_START_PROJECTOR:
                // プロジェクタマネージャの開始(プロジェクター利用時のみ).
                if(!isProjected) {
                    startService(getIntentForProjector());
                }
                break;
            case ScenarioDefinitions.FUNC_READ_NEXT:
                readNext();
                break;
            case ScenarioDefinitions.FUNC_READ_FIRST:
                readArticle(0);
            default:
                break;
        }
    }

    /**
     * タイトルバーを設定する.
     */
    private void setupTitleBar() {
//        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
//        setActionBar(toolbar);
    }

    /**
     * プロジェクターマネージャーの開始/停止用のIntentを設定する.
     */
    private Intent getIntentForProjector() {
        Intent intent = new Intent();
        ComponentName componentName = new ComponentName(
                ProjectorManagerServiceUtil.PACKAGE_NAME,
                ProjectorManagerServiceUtil.CLASS_NAME);
        //逆方向で照射する
        intent.putExtra(ProjectorManagerServiceUtil.EXTRA_PROJECTOR_OUTPUT, ProjectorManagerServiceUtil.EXTRA_PROJECTOR_OUTPUT_VAL_REVERSE);
        intent.setComponent(componentName);
        return intent;
    }

    /**
     * プロジェクターの状態変化イベントを受け取るためのレシーバーをセットする.
     */
    private void setProjectorEventReceiver() {
        Log.v(TAG, "setProjectorEventReceiver()");
        if (mProjectorEventReceiver == null) {
            mProjectorEventReceiver = new ProjectorEventReceiver();
        } else {
            return;
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_PREPARE);
        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_START);
        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_PAUSE);
        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_RESUME);
        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_END);
        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_END_ERROR);
        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_END_FATAL_ERROR);
        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_TERMINATE);
        registerReceiver(mProjectorEventReceiver, intentFilter);
    }

    /**
     * WakeLockを取得する.
     */
    private void acquireWakeLock() {
        Log.v(TAG, "acquireWakeLock()");
        PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
        synchronized (mLock) {
            if (mWakelock == null || !mWakelock.isHeld()) {
                mWakelock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK
                        | PowerManager.ACQUIRE_CAUSES_WAKEUP
                        | PowerManager.ON_AFTER_RELEASE, MainActivity.class.getName());
                mWakelock.acquire();
            }
        }
    }

    /**
     * WakeLockを開放する.
     */
    private void releaseWakeLock() {
        Log.v(TAG, "releaseWakeLock()");
        synchronized (mLock) {
            if (mWakelock != null && mWakelock.isHeld()) {
                mWakelock.release();
                mWakelock = null;
            }
        }
    }

    /**
     * ホームボタンの押下イベントを受け取るためのBroadcastレシーバークラス.<br>
     * <p/>
     * アプリは必ずホームボタンで終了する..
     */
    private class HomeEventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG, "Receive Home button pressed");
            // ホームボタン押下でアプリ終了する.
            finish();
        }
    }

    /**
     * 音声UI再起動イベントを受け取るためのBroadcastレシーバークラス.<br>
     * <p/>
     * 稀に音声UIのServiceが再起動することがあり、その場合アプリはVoiceUIの再取得とListenerの再登録をする.
     */
    private class VoiceUIStartReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (VoiceUIManager.ACTION_VOICEUI_SERVICE_STARTED.equals(action)) {
                Log.d(TAG, "VoiceUIStartReceiver#onReceive():VOICEUI_SERVICE_STARTED");
                //VoiceUIManagerのインスタンス取得.
                mVoiceUIManager = VoiceUIManager.getService(getApplicationContext());
                if (mMainActivityVoiceUIListener == null) {
                    mMainActivityVoiceUIListener = new MainActivityVoiceUIListener(getApplicationContext());
                }
                //VoiceUIListenerの登録.
                VoiceUIManagerUtil.registerVoiceUIListener(mVoiceUIManager, mMainActivityVoiceUIListener);
            }
        }
    }

    /**
     * プロジェクターの状態変化時のイベントを受け取るためのBroadcastレシーバークラス.<br>
     * <p/>
     * 照射開始時にはWakeLockの取得、終了時にはWakeLockの開放する.<br>
     * アプリ仕様に応じて必要な処理があれば実装すること.
     */
    private class ProjectorEventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG, "ProjectorEventReceiver#onReceive():" + intent.getAction());
            switch (intent.getAction()) {
                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_PREPARE:
                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_PAUSE:
                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_RESUME:
                    break;
                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_START:
                    acquireWakeLock();
                    isProjected = true;
                    break;
                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_END:
                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_END_FATAL_ERROR:
                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_END_ERROR:
                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_TERMINATE:
                    releaseWakeLock();
                    isProjected = false;
                    break;
                default:
                    break;
            }
        }
    }

    private void execAsync() {
        FetchArticles task = new FetchArticles(new FetchArticles.Callback() {
            @Override
            public void onFinished(JSONArray result) {
                articles = result;
                if (mVoiceUIManager != null) {
                    VoiceUIVariableListHelper helper = new VoiceUIVariableListHelper();
                    helper.addAccost(ScenarioDefinitions.ACC_READY);
                    VoiceUIManagerUtil.updateAppInfo(mVoiceUIManager, helper.getVariableList(), true);
                }
            }
        });

        task.execute();
    }

    private void readArticle() {
        int tempIndex = (int) (Math.random() * articles.length());
        readArticle(tempIndex);
    }

    private void readArticle(int index) {
        currentIndex = index;
//        imageView.setImageBitmap();

        try {
            JSONObject article = (JSONObject) articles.get(index);
            final String title = article.getString("title") + "。";
            final String content = article.getString("content").replaceAll("<.+?>", "").replaceAll("\\n", "。");
            JSONArray images = article.getJSONArray("images");
            JSONObject image = images.getJSONObject(0);

            String imageUrl = "https://api.yokotv.com/api/Media/" + image.getString("container") + "/download/" + image.getString("filename");
            FetchImage fetchImageTask = new FetchImage(imageUrl, new FetchImage.Callback() {
                @Override
                public void onFinished(Bitmap bitmap) {
                    imageView.setImageBitmap(bitmap);

                    if (mVoiceUIManager != null) {
                        VoiceUIVariableListHelper helper = new VoiceUIVariableListHelper();
                        helper.addAccost(ScenarioDefinitions.ACC_READ);
                        helper.addStringValue("content", title + content);
                        VoiceUIManagerUtil.updateAppInfo(mVoiceUIManager, helper.getVariableList(), true);
                    }
                }
            });
            fetchImageTask.execute();

        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private void readNext() {
        currentIndex = currentIndex + 1;
        Log.v(TAG, Integer.toString(currentIndex));
        readArticle(currentIndex);
    }

    private boolean isReady() {
        return articles != null;
    }
}
