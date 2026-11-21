package br.com.fariastemper.orcavidro;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQUEST_OPEN_FILE = 1101;
    private static final int REQUEST_SAVE_FILE = 1102;
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private byte[] exportContent;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        setContentView(web);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        web.addJavascriptInterface(new Bridge(), "OrcaVidroAndroid");
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView w,
                    ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    Intent i = params.createIntent();
                    startActivityForResult(i, REQUEST_OPEN_FILE);
                    return true;
                } catch (Exception e) {
                    fileCallback.onReceiveValue(null);
                    fileCallback = null;
                    Toast.makeText(MainActivity.this,"Não foi possível abrir o arquivo",Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("file:///android_asset/")) return false;
                openExternal(url);
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (url.startsWith("file:///android_asset/")) {
                    String js = "(function(){window.print=function(){OrcaVidroAndroid.print()};"+
                      "window.download=function(content,name,mime){OrcaVidroAndroid.saveJson(String(content),String(name));};"+
                      "})();";
                    view.evaluateJavascript(js, null);
                }
            }
        });
        web.loadUrl("file:///android_asset/index.html");
    }

    private void openExternal(String url) {
        if (!(url.startsWith("https://") || url.startsWith("http://") ||
              url.startsWith("mailto:") || url.startsWith("tel:"))) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))); }
        catch (ActivityNotFoundException e) {
            Toast.makeText(this,"Nenhum aplicativo disponível para abrir o link",Toast.LENGTH_SHORT).show();
        }
    }

    private class Bridge {
        @JavascriptInterface public void print() {
            runOnUiThread(() -> {
                PrintManager pm=(PrintManager)getSystemService(Context.PRINT_SERVICE);
                if(pm!=null)
                    pm.print("OrçaVidro - PDF",web.createPrintDocumentAdapter("OrçaVidro"),
                        new PrintAttributes.Builder().build());
            });
        }
        @JavascriptInterface public void saveJson(String text, String filename) {
            runOnUiThread(() -> {
                exportContent=text.getBytes(StandardCharsets.UTF_8);
                Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                String name=(filename == null || filename.trim().isEmpty())?
                    "orcavidro-backup.json":filename;
                i.putExtra(Intent.EXTRA_TITLE,name);
                try { startActivityForResult(i,REQUEST_SAVE_FILE); }
                catch (Exception e) {
                    exportContent=null;
                    Toast.makeText(MainActivity.this,"Não foi possível salvar",Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQUEST_OPEN_FILE) {
            ValueCallback<Uri[]> cb=fileCallback;
            fileCallback=null;
            if(cb!=null) {
                Uri[] result=(resultCode==RESULT_OK)?
                    WebChromeClient.FileChooserParams.parseResult(resultCode,data):null;
                cb.onReceiveValue(result);
            }
        } else if(requestCode==REQUEST_SAVE_FILE) {
            if(resultCode==RESULT_OK && data!=null &&
               data.getData()!=null && exportContent!=null) {
                try(OutputStream out=getContentResolver().openOutputStream(data.getData())) {
                    if(out!=null) out.write(exportContent);
                    Toast.makeText(this,"Backup salvo",Toast.LENGTH_SHORT).show();
                } catch(Exception e) {
                    Toast.makeText(this,"Falha ao salvar arquivo",Toast.LENGTH_LONG).show();
                }
            }
            exportContent=null;
        }
    }

    @Override public void onBackPressed() {
        if(web!=null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if(web!=null) {web.destroy(); web=null;}
        super.onDestroy();
    }
}
