package com.techcell.caixadaloja;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivityV25 extends Activity {
    private static final String CHANNEL_ID = "caixa_lancamentos";
    private static final int NOTIFICATION_PERMISSION_CODE = 9102;
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createNotificationChannel();
        requestNotificationPermissionIfNeeded();

        webView = new WebView(this);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }

        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("file".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                catch (Exception ignored) { Toast.makeText(MainActivityV25.this, "Não foi possível abrir este link.", Toast.LENGTH_SHORT).show(); }
                return true;
            }
        });

        if (savedInstanceState != null) webView.restoreState(savedInstanceState);
        else webView.loadDataWithBaseURL("file:///android_asset/", EmbeddedApp.html(), "text/html", "UTF-8", null);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Lançamentos do Caixa", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Avisos de novos lançamentos do caixa");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_CODE);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void playSuccessSound() {
            new Thread(() -> {
                try {
                    ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 38);
                    tone.startTone(ToneGenerator.TONE_PROP_ACK, 140);
                    Thread.sleep(220);
                    tone.release();
                } catch (Exception ignored) {}
            }).start();
        }

        @JavascriptInterface
        public void showNotification(String title, String message) {
            runOnUiThread(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
                    Intent intent = new Intent(MainActivityV25.this, MainActivityV25.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    PendingIntent pendingIntent = PendingIntent.getActivity(MainActivityV25.this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? new android.app.Notification.Builder(MainActivityV25.this, CHANNEL_ID)
                            : new android.app.Notification.Builder(MainActivityV25.this);
                    builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                            .setContentTitle(title == null ? "Caixa da Loja" : title)
                            .setContentText(message == null ? "Novo lançamento" : message)
                            .setStyle(new android.app.Notification.BigTextStyle().bigText(message == null ? "Novo lançamento" : message))
                            .setAutoCancel(true)
                            .setContentIntent(pendingIntent);
                    NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    if (manager != null) manager.notify((int) (System.currentTimeMillis() & 0x7fffffff), builder.build());
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public void saveBase64File(String fileName, String mimeType, String base64Data) {
            try {
                byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new IllegalStateException("Falha ao criar arquivo");
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        if (out == null) throw new IllegalStateException("Falha ao abrir arquivo");
                        out.write(bytes);
                    }
                } else {
                    File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (dir == null) throw new IllegalStateException("Pasta indisponível");
                    if (!dir.exists()) dir.mkdirs();
                    try (FileOutputStream out = new FileOutputStream(new File(dir, fileName))) { out.write(bytes); }
                }
                runOnUiThread(() -> Toast.makeText(MainActivityV25.this, "Planilha salva em Downloads.", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivityV25.this, "Não foi possível salvar a planilha.", Toast.LENGTH_LONG).show());
            }
        }
    }
}
