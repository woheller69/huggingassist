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

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
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

        chatWebView.setWebChromeClient(new WebChromeClient(){
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                if (consoleMessage.message().contains("NotAllowedError: Write permission denied.") || consoleMessage.message().contains("DOMException")) {  //this error occurs when user copies to clipboard
                    Toast.makeText(context, R.string.error_copy,Toast.LENGTH_LONG).show();
                    return true;
                }
                return false;
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
                for (String url : allowedDomains) {
                    if (request.getUrl().getHost().equals(url)) {
                        allowed = true;
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
                for (String url : allowedDomains) {
                    if (request.getUrl().getHost().equals(url)) {
                        allowed = true;
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
        chatWebSettings.setUserAgentString("Mozilla/5.0 (Linux; Android 12; Unspecified Device) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.79 Mobile Safari/537.36");

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
    }
}