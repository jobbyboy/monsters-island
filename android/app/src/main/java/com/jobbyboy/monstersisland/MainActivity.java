package com.jobbyboy.monstersisland;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    // Versione del "guscio" Android: va aumentata solo quando cambia questo file Java.
    static final int SHELL = 3;
    static final String BASE = "https://github.com/jobbyboy/monsters-island/releases/download/ultima/";

    private WebView web;
    private LinearLayout splash;
    private TextView status;
    private ProgressBar bar;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("gioco", MODE_PRIVATE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
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
        web.setVisibility(View.INVISIBLE);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));

        splash = new LinearLayout(this);
        splash.setOrientation(LinearLayout.VERTICAL);
        splash.setGravity(Gravity.CENTER);
        splash.setBackgroundColor(Color.rgb(26, 16, 8));
        TextView title = new TextView(this);
        title.setText("Monsters Island");
        title.setTextColor(Color.rgb(255, 214, 110));
        title.setTextSize(34);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        status = new TextView(this);
        status.setText("Controllo aggiornamenti…");
        status.setTextColor(Color.rgb(240, 225, 195));
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(14), 0, dp(10));
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setIndeterminate(true);
        splash.addView(title);
        splash.addView(status);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(280), dp(12));
        splash.addView(bar, lp);
        root.addView(splash, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        immersive();

        new Thread(this::updateThenStart).start();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private File gameDir() { File d = new File(getFilesDir(), "gioco"); d.mkdirs(); return d; }

    private void say(String t, int pct) {
        runOnUiThread(() -> {
            status.setText(t);
            if (pct < 0) bar.setIndeterminate(true); else { bar.setIndeterminate(false); bar.setProgress(pct); }
        });
    }

    private String fetchText(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(6000); c.setReadTimeout(8000); c.setInstanceFollowRedirects(true);
        if (c.getResponseCode() != 200) throw new Exception("http " + c.getResponseCode());
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder(); String l; while ((l = r.readLine()) != null) sb.append(l); r.close();
        return sb.toString();
    }

    private int num(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return -1;
        String t = json.substring(json.indexOf(':', i) + 1).replaceAll("^[^0-9]*", "");
        StringBuilder n = new StringBuilder();
        for (char ch : t.toCharArray()) { if (Character.isDigit(ch)) n.append(ch); else break; }
        return n.length() == 0 ? -1 : Integer.parseInt(n.toString());
    }

    private void download(String url, File out, String label) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(20000); c.setInstanceFollowRedirects(true);
        if (c.getResponseCode() != 200) throw new Exception("http " + c.getResponseCode());
        long total = c.getContentLengthLong(), got = 0;
        File tmp = new File(out.getPath() + ".tmp");
        try (InputStream in = c.getInputStream(); FileOutputStream fo = new FileOutputStream(tmp)) {
            byte[] buf = new byte[65536]; int n; int last = -1;
            while ((n = in.read(buf)) > 0) {
                fo.write(buf, 0, n); got += n;
                if (total > 0) { int p = (int) (got * 100 / total); if (p != last) { last = p; say(label + " " + p + "%", p); } }
            }
        }
        if (total > 0 && got != total) { tmp.delete(); throw new Exception("incompleto"); }
        if (!tmp.renameTo(out)) throw new Exception("rinomina");
    }

    private void updateThenStart() {
        boolean shellUpdate = false;
        try {
            String js = fetchText(BASE + "version.json");
            int remoteGame = num(js, "game");
            int remoteShell = num(js, "shell");
            shellUpdate = remoteShell > SHELL;
            File html = new File(gameDir(), "index.html");
            int bundled = bundledVersion();
            int localGame = Math.max(bundled, html.exists() ? prefs.getInt("gameV", 0) : 0);
            if (remoteGame > localGame) {
                say("Scarico la nuova versione del gioco…", 0);
                File three = new File(gameDir(), "three.min.js");
                if (!three.exists()) download(BASE + "three.min.js", three, "Preparo il motore 3D…");
                download(BASE + "index.html", html, "Scarico l'aggiornamento…");
                prefs.edit().putInt("gameV", remoteGame).apply();
                say("Aggiornamento completato!", 100);
            } else {
                say("Hai già l'ultima versione", 100);
            }
        } catch (Exception e) {
            say("Nessuna connessione: avvio la versione salvata", -1);
        }
        final boolean askShell = shellUpdate;
        runOnUiThread(() -> startGame(askShell));
    }

    private int bundledVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode; } catch (Exception e) { return 1; }
    }

    private void startGame(boolean askShell) {
        File html = new File(gameDir(), "index.html");
        boolean useDownloaded = html.exists() && new File(gameDir(), "three.min.js").exists()
                && prefs.getInt("gameV", 0) > bundledVersion();
        String url = useDownloaded ? Uri.fromFile(html).toString() : "file:///android_asset/index.html";
        web.loadUrl(url);
        web.postDelayed(() -> { splash.setVisibility(View.GONE); web.setVisibility(View.VISIBLE); }, 900);
        if (askShell) web.postDelayed(this::askApkUpdate, 2500);
    }

    private void askApkUpdate() {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
            .setTitle("Nuova app disponibile")
            .setMessage("Questa volta serve reinstallare l'app di Monsters Island. Vuoi scaricarla adesso? I salvataggi restano.")
            .setPositiveButton("Aggiorna all'ultima versione", (d, w) ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(BASE + "MonstersIsland.apk"))))
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
