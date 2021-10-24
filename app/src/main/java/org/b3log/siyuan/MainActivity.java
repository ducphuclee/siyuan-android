/*
 * SiYuan - 源于思考，饮水思源
 * Copyright (c) 2020-present, ld246.com
 *
 * 本文件属于思源笔记源码的一部分，云南链滴科技有限公司版权所有。
 */
package org.b3log.siyuan;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

import mobile.Mobile;

/**
 * 程序入口.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.0.0.2, Jul 24, 2021
 * @since 1.0.0
 */
public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private ProgressBar bootProgressBar;
    private TextView bootDetailsText;
    private int bootProgress;
    private String bootDetails;
    private Handler handler;
    private String version;

    public ValueCallback<Uri[]> uploadMessage;
    public static final int REQUEST_SELECT_FILE = 100;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        final View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
        setContentView(R.layout.activity_main);
        initVersion();

        bootProgressBar = findViewById(R.id.progressBar);
        bootDetailsText = findViewById(R.id.bootDetails);
        bootDetailsText.setText("Booting...");
        webView = findViewById(R.id.webView);
        webView.setVisibility(View.GONE);
        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(WebView mWebView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
                if (uploadMessage != null) {
                    uploadMessage.onReceiveValue(null);
                    uploadMessage = null;
                }

                uploadMessage = filePathCallback;
                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, REQUEST_SELECT_FILE);
                } catch (final Exception e) {
                    uploadMessage = null;
                    Toast.makeText(getApplicationContext(), "Cannot open file chooser", Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }
        });

        handler = new Handler(Looper.getMainLooper()) {
            public void handleMessage(final Message msg) {
                showMainUI();
            }
        };

        new Thread(this::boot).start();
        new Thread(this::bootProgress).start();
        new Thread(this::keepLive).start();
    }

    private void keepLive() {
        // 通知栏保活
        while (true) {
            final Intent intent = new Intent(MainActivity.this, WhiteService.class);
            startService(intent);
            sleep(45 * 1000);
            stopService(intent);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showMainUI() {
        bootProgressBar.setVisibility(View.GONE);
        bootDetailsText.setVisibility(View.GONE);
        final ImageView bootLogo = findViewById(R.id.bootLogo);
        bootLogo.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);

        AndroidBug5497Workaround.assistActivity(this);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(final WebView view, final String url) {
                if (url.contains("127.0.0.1")) {
                    view.loadUrl(url);
                } else {
                    final Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    final ActivityInfo info = i.resolveActivityInfo(getPackageManager(), PackageManager.MATCH_ALL);
                    if (null == info || !info.exported) {
//                        Toast.makeText(getApplicationContext(), "No application that can handle this link [" + url + "]", Toast.LENGTH_LONG).show();
                    } else {
                        startActivity(i);
                    }
                }
                return true;
            }
        });

        final JSAndroid JSAndroid = new JSAndroid(this);
        webView.addJavascriptInterface(JSAndroid, "JSAndroid");
        final WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
        ws.setTextZoom(100);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUserAgentString("SiYuan/" + version + " https://b3log.org/siyuan " + ws.getUserAgentString());
        webView.loadUrl("http://127.0.0.1:6806");
    }

    private void boot() {
        final String dataDir = getFilesDir().getAbsolutePath();
        final String appDir = dataDir + "/app";
        new File(appDir).mkdirs();
        try {
            FileUtils.deleteDirectory(new File(appDir));
        } catch (final Exception e) {
            Log.wtf("", "Delete dir [" + appDir + "] failed, exit application", e);
            System.exit(-1);
        }

        final String libDir = dataDir + "/lib";
        try {
            FileUtils.deleteDirectory(new File(libDir));
        } catch (final Exception e) {
            Log.wtf("", "Delete dir [" + libDir + "] failed, exit application", e);
            System.exit(-1);
        }

        Utils.unzipAsset(getAssets(), "app.zip", appDir + "/app");
        Utils.unzipAsset(getAssets(), "lib.zip", libDir);

        final Locale locale = getResources().getConfiguration().locale;
        final String lang = locale.getLanguage() + "_" + locale.getCountry();
        Mobile.setDefaultLang(lang);
        final String localIP = Utils.getIpAddressString();
        final String workspaceDir = getWorkspacePath();
        Mobile.startKernel("android", appDir, workspaceDir, getApplicationInfo().nativeLibraryDir, dataDir, localIP);
    }

    private void bootProgress() {
        sleep(500);
        while (true) {
            sleep(100);

            HttpURLConnection urlConnection = null;
            try {
                final URL bootProgressURL = new URL("http://127.0.0.1:6806/api/system/bootProgress");
                urlConnection = (HttpURLConnection) bootProgressURL.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.setDefaultUseCaches(false);
                urlConnection.setConnectTimeout(500);
                urlConnection.setReadTimeout(1000);
                final InputStream inputStream = urlConnection.getInputStream();
                final String content = IOUtils.toString(inputStream);
                final JSONObject result = new JSONObject(content);
                final JSONObject data = result.optJSONObject("data");
                bootDetails = data.optString("details");
                bootProgress = data.optInt("progress");
                runOnUiThread(() -> {
                    bootDetailsText.setText(bootDetails);
                    bootProgressBar.setProgress(bootProgress);
                });
                if (100 <= bootProgress) {
                    handler.sendEmptyMessage(0);
                    return;
                }
            } catch (final Throwable e) {
                // ignored
                //e.printStackTrace();
            } finally {
                if (null != urlConnection) {
                    urlConnection.disconnect();
                }
            }
        }
    }

    private String getWorkspacePath() {
        return getExternalFilesDir("siyuan").getAbsolutePath();
    }

    private void initVersion() {
        try {
            final PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = pInfo.versionName;
        } catch (final PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void sleep(final long time) {
        try {
            Thread.sleep(time);
        } catch (final Exception e) {
            Log.e("", e.getMessage());
        }
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            webView.evaluateJavascript("javascript:window.goBack()", null);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        webView.evaluateJavascript("javascript:window.goBack()", null);
    }

    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        AndroidBug5497Workaround.assistActivity(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_SELECT_FILE) {
            if (uploadMessage == null) {
                return;
            }
            uploadMessage.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, intent));
            uploadMessage = null;
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }
}