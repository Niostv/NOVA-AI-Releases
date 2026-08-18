package ai.nova.app;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.provider.Settings;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int MIC_REQUEST = 41;
    private static final int FILE_REQUEST = 42;
    private WebView mainWebView;
    private ValueCallback<Uri[]> fileCallback;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(7, 7, 11));
        getWindow().setNavigationBarColor(Color.rgb(7, 7, 11));
        mainWebView = makeWebView(null);
        setContentView(mainWebView);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_REQUEST);
        mainWebView.loadUrl("https://nova.local/index.html");
    }

    private WebView makeWebView(final Dialog popup) {
        WebView view = new WebView(this);
        view.setBackgroundColor(Color.rgb(7, 7, 11));
        WebSettings s = view.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccess(true);
        s.setUserAgentString(s.getUserAgentString() + " NOVAAndroid/1.0");
        view.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        view.addJavascriptInterface(new AndroidUpdater(this, view), "NovaAndroidUpdaterNative");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true);
        view.setWebViewClient(new NovaClient());
        view.setWebChromeClient(new NovaChromeClient(popup));
        return view;
    }

    private class NovaClient extends WebViewClient {
        @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (!"nova.local".equals(uri.getHost())) return super.shouldInterceptRequest(view, request);
            String path = uri.getPath();
            if (path == null || path.equals("/")) path = "/index.html";
            path = path.substring(1);
            if (path.contains("..")) return null;
            try {
                InputStream data = getAssets().open(path);
                String ext = MimeTypeMap.getFileExtensionFromUrl(path);
                String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                if (mime == null) mime = "application/octet-stream";
                return new WebResourceResponse(mime, "UTF-8", data);
            } catch (Exception ignored) { return null; }
        }
        @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String scheme = uri.getScheme();
            if ("http".equals(scheme) || "https".equals(scheme)) return false;
            try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) {}
            return true;
        }
    }

    private class NovaChromeClient extends WebChromeClient {
        private final Dialog popup;
        NovaChromeClient(Dialog popup) { this.popup = popup; }

        @Override public void onPermissionRequest(final PermissionRequest request) {
            runOnUiThread(() -> {
                List<String> granted = new ArrayList<>();
                for (String resource : request.getResources()) {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource) && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                        granted.add(resource);
                }
                if (granted.isEmpty()) request.deny(); else request.grant(granted.toArray(new String[0]));
            });
        }

        @Override public boolean onCreateWindow(WebView source, boolean dialog, boolean userGesture, Message resultMsg) {
            final Dialog authDialog = new Dialog(MainActivity.this, android.R.style.Theme_Material_NoActionBar);
            WebView child = makeWebView(authDialog);
            authDialog.setContentView(child, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            authDialog.setOnDismissListener(d -> child.destroy());
            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(child);
            resultMsg.sendToTarget();
            authDialog.show();
            return true;
        }

        @Override public void onCloseWindow(WebView window) {
            if (popup != null && popup.isShowing()) popup.dismiss();
        }

        @Override public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = callback;
            try {
                Intent intent = params.createIntent();
                startActivityForResult(intent, FILE_REQUEST);
                return true;
            } catch (Exception e) {
                fileCallback = null;
                return false;
            }
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_REQUEST && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        }
    }

    @Override public void onBackPressed() {
        if (mainWebView.canGoBack()) mainWebView.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (mainWebView != null) mainWebView.destroy();
        super.onDestroy();
    }
}
