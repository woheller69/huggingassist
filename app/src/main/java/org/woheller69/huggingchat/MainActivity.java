/*
Copyright (c) 2017-2019 Divested Computing Group
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/
package org.woheller69.huggingchat;

import static android.webkit.WebView.HitTestResult.IMAGE_TYPE;
import static android.webkit.WebView.HitTestResult.SRC_ANCHOR_TYPE;
import static android.webkit.WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends Activity {

    private SwipeTouchListener swipeTouchListener;
    private Button resetButton = null;
    private WebView chatWebView = null;
    private WebSettings chatWebSettings = null;
    private CookieManager chatCookieManager = null;
    private final Context context = this;
    private String TAG ="huggingChat";
    private String urlToLoad = "https://huggingface.co/chat/";

    private static final ArrayList<String> allowedDomains = new ArrayList<String>();

    private ValueCallback<Uri[]> mUploadMessage;
    private final static int FILE_CHOOSER_REQUEST_CODE = 1;

    @Override
    protected void onPause() {
        if (chatCookieManager!=null) chatCookieManager.flush();
        swipeTouchListener = null;
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        swipeTouchListener = new SwipeTouchListener(context) {
            public void onSwipeBottom() {
                if (!chatWebView.canScrollVertically(0)) {
                    resetButton.setVisibility(View.VISIBLE);
                }
            }
            public void onSwipeTop(){
                resetButton.setVisibility(View.GONE);
            }
        };

        chatWebView.setOnTouchListener(swipeTouchListener);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setTheme(android.R.style.Theme_DeviceDefault_DayNight);
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Create the WebView
        chatWebView = findViewById(R.id.chatWebView);
        resetButton = findViewById(R.id.resetButton);

        //Set cookie options
        chatCookieManager = CookieManager.getInstance();
        chatCookieManager.setAcceptCookie(true);
        chatCookieManager.setAcceptThirdPartyCookies(chatWebView, false);

        //Restrict what gets loaded
        initURLs();
        registerForContextMenu(chatWebView);

        chatWebView.setWebChromeClient(new WebChromeClient(){
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                if (consoleMessage.message().contains("NotAllowedError: Write permission denied.") || consoleMessage.message().contains("DOMException")) {  //this error occurs when user copies to clipboard
                    Toast.makeText(context, R.string.error_copy,Toast.LENGTH_LONG).show();
                    return true;
                }
                return false;
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 100);
                    }
                }
                if (mUploadMessage != null) {
                    mUploadMessage.onReceiveValue(null);
                    mUploadMessage = null;
                }

                mUploadMessage = filePathCallback;

                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
                return true;
            }
        });  //needed to share link

        chatWebView.setWebViewClient(new WebViewClient() {
            //Keep these in sync!
            @Override
            public WebResourceResponse shouldInterceptRequest(final WebView view, WebResourceRequest request) {
                if (request.getUrl().toString().equals("about:blank")) {
                    return null;
                }
                if (!request.getUrl().toString().startsWith("https://")) {
                    Log.d(TAG, "[shouldInterceptRequest][NON-HTTPS] Blocked access to " + request.getUrl().toString());
                    return new WebResourceResponse("text/javascript", "UTF-8", null); //Deny URLs that aren't HTTPS
                }
                boolean allowed = false;
                for (String domain : allowedDomains) {
                    if (request.getUrl().getHost().endsWith(domain)) {
                        allowed = true;
                        break;
                    }
                }
                if (!allowed) {
                    Log.d(TAG, "[shouldInterceptRequest][NOT ON ALLOWLIST] Blocked access to " + request.getUrl().getHost());
                    return new WebResourceResponse("text/javascript", "UTF-8", null); //Deny URLs not on ALLOWLIST
                }
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request.getUrl().toString().equals("about:blank")) {
                    return false;
                }
                if (!request.getUrl().toString().startsWith("https://")) {
                    Log.d(TAG, "[shouldOverrideUrlLoading][NON-HTTPS] Blocked access to " + request.getUrl().toString());
                    return true; //Deny URLs that aren't HTTPS
                }
                boolean allowed = false;
                for (String domain : allowedDomains) {
                    if (request.getUrl().getHost().endsWith(domain)) {
                        allowed = true;
                        break;
                    }
                }
                if (!allowed) {
                    Log.d(TAG, "[shouldOverrideUrlLoading][NOT ON ALLOWLIST] Blocked access to " + request.getUrl().getHost());
                    return true; //Deny URLs not on ALLOWLIST
                }
                return false;
            }
        });

        //Set more options
        chatWebSettings = chatWebView.getSettings();
        //Enable some WebView features
        chatWebSettings.setJavaScriptEnabled(true);
        chatWebSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        chatWebSettings.setGeolocationEnabled(false);
        //Disable some WebView features
        chatWebSettings.setAllowContentAccess(false);
        chatWebSettings.setAllowFileAccess(false);
        chatWebSettings.setBuiltInZoomControls(false);
        chatWebSettings.setDatabaseEnabled(false);
        chatWebSettings.setDisplayZoomControls(false);
        chatWebSettings.setDomStorageEnabled(true);
        chatWebSettings.setSaveFormData(false);
        //Change the User-Agent
        chatWebSettings.setUserAgentString("Mozilla/5.0 (Linux; Unspecified Device) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.79 Mobile Safari/537.36");

        //Load HuggingChat
        chatWebView.loadUrl(urlToLoad);
        if (GithubStar.shouldShowStarDialog(this)) GithubStar.starDialog(this,"https://github.com/woheller69/huggingassist");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        //Credit (CC BY-SA 3.0): https://stackoverflow.com/a/6077173
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_BACK:
                    if (chatWebView.canGoBack() && !chatWebView.getUrl().equals("about:blank")) {
                        chatWebView.goBack();
                    } else {
                        finish();
                    }
                    return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    public void resetChat(View view)  {

        chatWebView.clearFormData();
        chatWebView.clearHistory();
        chatWebView.clearMatches();
        chatWebView.clearSslPreferences();
        chatCookieManager.removeSessionCookie();
        chatCookieManager.removeAllCookie();
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        WebStorage.getInstance().deleteAllData();
        chatWebView.loadUrl(urlToLoad);


    }


    private static void initURLs() {
        //Allowed Domains
        allowedDomains.add("huggingface.co");
        allowedDomains.add("pollinations.ai");
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (mUploadMessage == null) return;
            Uri[] result = null;
            if (resultCode == Activity.RESULT_OK) {
                if (intent != null) {
                    String dataString = intent.getDataString();
                    if (dataString != null) {
                        result = new Uri[]{Uri.parse(dataString)};
                    }
                }
            }
            mUploadMessage.onReceiveValue(result);
            mUploadMessage = null;
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo){
        super.onCreateContextMenu(menu, v, menuInfo);
        WebView.HitTestResult result = chatWebView.getHitTestResult();
        String url="";
        if (result.getExtra() != null) {
            if (result.getType() == IMAGE_TYPE){
                url = result.getExtra();
                Toast.makeText(this,"IMAGE: "+url,Toast.LENGTH_SHORT).show();
                if (url != null && !url.isEmpty() &&!url.contains("/avatar.jpg?")) {
                    Toast.makeText(this,getString(R.string.downloading),Toast.LENGTH_LONG).show();
                    String filename = URLUtil.guessFileName(url, null, "image/jpeg");
                    Uri source = Uri.parse(url);
                    DownloadManager.Request request = new DownloadManager.Request(source);
                    request.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
                    request.addRequestHeader("Accept", "text/html, application/xhtml+xml, *" + "/" + "*");
                    request.addRequestHeader("Accept-Language", "en-US,en;q=0.7,he;q=0.3");
                    request.addRequestHeader("Referer", url);
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED); //Notify client once download is completed!
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
                    DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    assert dm != null;
                    dm.enqueue(request);
                }
            } else if (result.getType() == SRC_IMAGE_ANCHOR_TYPE || result.getType() == SRC_ANCHOR_TYPE){
                if (result.getType() == SRC_IMAGE_ANCHOR_TYPE) {
                    // Create a background thread that has a Looper
                    HandlerThread handlerThread = new HandlerThread("HandlerThread");
                    handlerThread.start();
                    // Create a handler to execute tasks in the background thread.
                    Handler backgroundHandler = new Handler(handlerThread.getLooper());
                    Message msg = backgroundHandler.obtainMessage();
                    chatWebView.requestFocusNodeHref(msg);
                    url = (String) msg.getData().get("url");
                    Toast.makeText(this,"SRC_IMAGE: "+url,Toast.LENGTH_SHORT).show();
                } else if (result.getType() == SRC_ANCHOR_TYPE) {
                    url=result.getExtra();
                    Toast.makeText(this,"SRC_ANCHOR: "+url,Toast.LENGTH_SHORT).show();
                }
                String host = Uri.parse(url).getHost();
                if (host!=null){
                    boolean allowed = false;
                    for (String domain : allowedDomains) {
                        if (host.endsWith(domain)) {
                            allowed = true;
                            break;
                        }
                    }
                    if (!allowed) {  //Copy URLs that are not allowed to open to clipboard
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText(getString(R.string.app_name), url);
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(this,getString(R.string.url_copied),Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

}
