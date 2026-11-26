package com.jobbyboy.monstersisland;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient());
        setContentView(web);
        immersive();
        web.loadUrl("file:///android_asset/index.html");
        checkUpdate();
    }

    private static final String BASE = "https://github.com/jobbyboy/monsters-island/releases/download/ultima/";

    private void checkUpdate() {
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(BASE + "version.json").openConnection();
                c.setConnectTimeout(6000); c.setReadTimeout(6000); c.setInstanceFollowRedirects(true);
                if (c.getResponseCode() != 200) return;
                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
                StringBuilder sb = new StringBuilder(); String l; while ((l = r.readLine()) != null) sb.append(l); r.close();
                String t = sb.toString().replaceAll("[^0-9]", "");
                if (t.isEmpty()) return;
                int latest = Integer.parseInt(t);
                int mine = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                if (latest > mine) runOnUiThread(this::askUpdate);
            } catch (Exception ignored) { }
        }).start();
    }

    private void askUpdate() {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
            .setTitle("Nuova versione disponibile")
            .setMessage("È uscito un aggiornamento di Monsters Island. Vuoi scaricarlo adesso? I salvataggi restano.")
            .setPositiveButton("Aggiorna all'ultima versione", (d, w) -> {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(BASE + "MonstersIsland.apk")));
            })
            .setNegativeButton("Più tardi", null)
            .show();
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f) immersive(); }
    @Override protected void onPause() { super.onPause(); web.onPause(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); immersive(); }
    @Override public void onBackPressed() { web.evaluateJavascript("(function(){try{pressB()}catch(e){}})()", null); }
}
