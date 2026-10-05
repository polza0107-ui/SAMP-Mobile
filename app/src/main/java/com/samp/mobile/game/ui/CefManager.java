package com.samp.mobile.game.ui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import com.samp.mobile.game.SAMP;

public class CefManager {
    private static final String TAG = "CefManager";
    private static CefManager sInstance;

    private final Activity mActivity;
    private FrameLayout mContainer;
    private WebView mWebView;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private boolean mIsVisible = false;
    private int mCurrentBrowserId = 0;

    public CefManager(Activity activity) {
        this.mActivity = activity;
        sInstance = this;
        initUI();
    }

    public static CefManager getInstance() {
        return sInstance;
    }

    private void initUI() {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mContainer = new FrameLayout(mActivity);
                mContainer.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));
                mContainer.setBackgroundColor(Color.TRANSPARENT);
                mContainer.setVisibility(View.GONE);

                mWebView = new WebView(mActivity);
                mWebView.setLayoutParams(new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                ));
                mWebView.setBackgroundColor(Color.TRANSPARENT);

                WebSettings settings = mWebView.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setDomStorageEnabled(true);
                settings.setDatabaseEnabled(true);
                settings.setAllowFileAccess(true);
                settings.setAllowContentAccess(true);
                settings.setAllowFileAccessFromFileURLs(true);
                settings.setAllowUniversalAccessFromFileURLs(true);
                settings.setLoadsImagesAutomatically(true);
                settings.setMediaPlaybackRequiresUserGesture(false);
                settings.setUseWideViewPort(true);
                settings.setLoadWithOverviewMode(true);
                settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

                // Add CEF bridge interface to simulate cef.emit and cef.on
                mWebView.addJavascriptInterface(new CefJavaScriptBridge(), "androidCef");

                mWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        super.onPageFinished(view, url);
                        injectCefPolyfill(view);
                    }

                    @Override
                    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                        super.onReceivedError(view, request, error);
                        Log.e(TAG, "WebView error: " + error.toString());
                    }
                });

                mWebView.setWebChromeClient(new WebChromeClient());

                mContainer.addView(mWebView);
                mActivity.addContentView(mContainer, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));
            }
        });
    }

    private void injectCefPolyfill(WebView webView) {
        // Polyfill window.cef object matching PC sampcef API
        String js = "javascript:(function() {" +
                "if (typeof window.cef === 'undefined') {" +
                "   window.cef = {" +
                "       _listeners: {}," +
                "       on: function(event, callback) {" +
                "           if (!this._listeners[event]) this._listeners[event] = [];" +
                "           this._listeners[event].push(callback);" +
                "       }," +
                "       emit: function(event) {" +
                "           var args = [];" +
                "           for (var i = 1; i < arguments.length; i++) args.push(arguments[i]);" +
                "           if (window.androidCef && window.androidCef.emitEvent) {" +
                "               window.androidCef.emitEvent(event, JSON.stringify(args));" +
                "           }" +
                "       }," +
                "       _trigger: function(event, args) {" +
                "           if (this._listeners[event]) {" +
                "               for (var i = 0; i < this._listeners[event].length; i++) {" +
                "                   try { this._listeners[event][i].apply(null, args); } catch (e) { console.error(e); }" +
                "               }" +
                "           }" +
                "       }" +
                "   };" +
                "}" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    public void showBrowser(final int browserId, final String url) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                mCurrentBrowserId = browserId;
                mIsVisible = true;
                if (mContainer != null) mContainer.setVisibility(View.VISIBLE);
                if (mWebView != null) {
                    mWebView.setVisibility(View.VISIBLE);
                    mWebView.loadUrl(url);
                }
            }
        });
    }

    public void hideBrowser() {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                mIsVisible = false;
                if (mContainer != null) mContainer.setVisibility(View.GONE);
                if (mWebView != null) {
                    mWebView.setVisibility(View.GONE);
                    mWebView.loadUrl("about:blank");
                }
            }
        });
    }

    public void emitToWeb(final String eventName, final String jsonArgs) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mWebView != null && mIsVisible) {
                    String script = String.format("if (window.cef && window.cef._trigger) { window.cef._trigger('%s', %s); }",
                            eventName, jsonArgs);
                    mWebView.evaluateJavascript(script, null);
                }
            }
        });
    }

    public boolean isVisible() {
        return mIsVisible;
    }

    public class CefJavaScriptBridge {
        @JavascriptInterface
        public void emitEvent(String event, String jsonArgs) {
            Log.d(TAG, "CEF Event from JS: " + event + " Args: " + jsonArgs);

            if ("cef:onLoginSubmit".equals(event)) {
                // Parse password argument from jsonArgs array [ "password" ]
                String pass = extractFirstArg(jsonArgs);
                if (SAMP.getInstance() != null && SAMP.getInstance().getDialogManager() != null) {
                    try {
                        byte[] strBytes = pass.getBytes("TIS-620");
                        SAMP.getInstance().getDialogManager().sendDialogResponse(0, 1100, 1, strBytes);
                    } catch (Exception e) {
                        SAMP.getInstance().getDialogManager().sendDialogResponse(0, 1100, 1, pass.getBytes());
                    }
                }
                hideBrowser();
            } else if ("cef:onRegisterSubmit".equals(event)) {
                String pass = extractFirstArg(jsonArgs);
                if (SAMP.getInstance() != null && SAMP.getInstance().getDialogManager() != null) {
                    try {
                        byte[] strBytes = pass.getBytes("TIS-620");
                        SAMP.getInstance().getDialogManager().sendDialogResponse(0, 1101, 1, strBytes);
                    } catch (Exception e) {
                        SAMP.getInstance().getDialogManager().sendDialogResponse(0, 1101, 1, pass.getBytes());
                    }
                }
                hideBrowser();
            } else if ("cef:selectSchool".equals(event)) {
                String school = extractFirstArg(jsonArgs);
                int listitem = 0;
                if ("itr".equalsIgnoreCase(school) || "inthar".equalsIgnoreCase(school)) listitem = 1;
                else if ("pcc".equalsIgnoreCase(school) || "prachachuen".equalsIgnoreCase(school)) listitem = 2;
                else if ("brp".equalsIgnoreCase(school) || "buranapon".equalsIgnoreCase(school)) listitem = 3;

                if (SAMP.getInstance() != null && SAMP.getInstance().getDialogManager() != null) {
                    try {
                        SAMP.getInstance().getDialogManager().sendDialogResponse(listitem, 1099, 1, school.getBytes());
                    } catch (Exception ignored) {}
                }
                hideBrowser();
            } else if ("cef:closeUI".equals(event) || "cef:closeInventory".equals(event) || "cef:closeCard".equals(event)) {
                hideBrowser();
            }
        }


        private String extractFirstArg(String jsonArgs) {
            try {
                org.json.JSONArray arr = new org.json.JSONArray(jsonArgs);
                if (arr.length() > 0) {
                    return arr.getString(0);
                }
            } catch (Exception ignored) {}
            return "";
        }
    }
}
