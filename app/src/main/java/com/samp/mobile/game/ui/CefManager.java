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
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.samp.mobile.game.SAMP;

public class CefManager {
    private static final String TAG = "CefManager";
    private static CefManager sInstance;

    private final Activity mActivity;
    private FrameLayout mContainer;
    private WebView mWebView;
    private FrameLayout mNotifyContainer;
    private View mCurrentToastView = null;
    private Runnable mDismissNotifyRunnable = null;
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

                mNotifyContainer = new FrameLayout(mActivity);
                FrameLayout.LayoutParams notifyLp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                notifyLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                mNotifyContainer.setLayoutParams(notifyLp);
                mNotifyContainer.setClickable(false);
                mNotifyContainer.setFocusable(false);
                mActivity.addContentView(mNotifyContainer, notifyLp);
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

    public void showNotification(final String rawData) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mActivity == null || mNotifyContainer == null) return;

                String type = "info";
                String tag = "NOTIFICATION";
                String msg = rawData != null ? rawData : "";

                if (rawData != null && rawData.contains("|")) {
                    String[] parts = rawData.split("\\|", 3);
                    if (parts.length >= 3) {
                        type = parts[0].trim().toLowerCase();
                        tag = parts[1].trim();
                        msg = parts[2].trim();
                    } else if (parts.length == 2) {
                        type = parts[0].trim().toLowerCase();
                        msg = parts[1].trim();
                    }
                }

                int accentColor = Color.parseColor("#38BDF8");
                String iconSymbol = "ℹ";

                if ("success".equals(type)) {
                    accentColor = Color.parseColor("#10B981");
                    iconSymbol = "✓";
                } else if ("error".equals(type)) {
                    accentColor = Color.parseColor("#EF4444");
                    iconSymbol = "✕";
                } else if ("warning".equals(type)) {
                    accentColor = Color.parseColor("#F59E0B");
                    iconSymbol = "⚠";
                }

                if (mDismissNotifyRunnable != null) {
                    mMainHandler.removeCallbacks(mDismissNotifyRunnable);
                    mDismissNotifyRunnable = null;
                }
                if (mCurrentToastView != null) {
                    mNotifyContainer.removeView(mCurrentToastView);
                    mCurrentToastView = null;
                }

                final LinearLayout card = new LinearLayout(mActivity);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setClickable(false);
                card.setFocusable(false);

                FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                cardLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                cardLp.topMargin = dpToPx(38);
                cardLp.leftMargin = dpToPx(24);
                cardLp.rightMargin = dpToPx(24);
                card.setLayoutParams(cardLp);
                card.setPadding(dpToPx(14), dpToPx(10), dpToPx(18), dpToPx(10));

                GradientDrawable bg = new GradientDrawable();
                bg.setColor(Color.parseColor("#E6111827"));
                bg.setCornerRadius(dpToPx(12));
                bg.setStroke(dpToPx(1.5f), accentColor);
                card.setBackground(bg);
                card.setElevation(dpToPx(6));

                // Icon
                TextView tvIcon = new TextView(mActivity);
                tvIcon.setText(iconSymbol);
                tvIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                tvIcon.setTextColor(accentColor);
                tvIcon.setTypeface(Typeface.DEFAULT_BOLD);
                tvIcon.setGravity(Gravity.CENTER);

                GradientDrawable iconBg = new GradientDrawable();
                iconBg.setShape(GradientDrawable.OVAL);
                iconBg.setColor(Color.argb(45, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)));
                tvIcon.setBackground(iconBg);

                int iconSize = dpToPx(28);
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
                iconLp.rightMargin = dpToPx(10);
                tvIcon.setLayoutParams(iconLp);
                card.addView(tvIcon);

                // Text column
                LinearLayout textCol = new LinearLayout(mActivity);
                textCol.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                textCol.setLayoutParams(colLp);

                // Tag
                TextView tvTag = new TextView(mActivity);
                tvTag.setText(tag.toUpperCase());
                tvTag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
                tvTag.setTextColor(accentColor);
                tvTag.setTypeface(Typeface.DEFAULT_BOLD);
                tvTag.setLetterSpacing(0.08f);
                textCol.addView(tvTag);

                // Message
                TextView tvMsg = new TextView(mActivity);
                tvMsg.setText(msg);
                tvMsg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
                tvMsg.setTextColor(Color.parseColor("#F9FAFB"));
                textCol.addView(tvMsg);

                card.addView(textCol);

                mCurrentToastView = card;
                mNotifyContainer.addView(card);

                card.setAlpha(0f);
                card.setTranslationY(-dpToPx(20));
                card.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(220)
                        .start();

                mDismissNotifyRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (mCurrentToastView == card) {
                            card.animate()
                                    .alpha(0f)
                                    .translationY(-dpToPx(15))
                                    .setDuration(250)
                                    .withEndAction(new Runnable() {
                                        @Override
                                        public void run() {
                                            mNotifyContainer.removeView(card);
                                            if (mCurrentToastView == card) {
                                                mCurrentToastView = null;
                                            }
                                        }
                                    })
                                    .start();
                        }
                    }
                };
                mMainHandler.postDelayed(mDismissNotifyRunnable, 3500);
            }
        });
    }

    private int dpToPx(float dp) {
        return Math.round(dp * mActivity.getResources().getDisplayMetrics().density);
    }

    public class CefJavaScriptBridge {
        @JavascriptInterface
        public void emitEvent(String event, String jsonArgs) {
            Log.d(TAG, "CEF Event from JS: " + event + " Args: " + jsonArgs);

            if ("cef:onLoginSubmit".equals(event)) {
                // Parse password argument from jsonArgs array [ "password" ]
                final String pass = extractFirstArg(jsonArgs);
                if (SAMP.getInstance() != null && SAMP.getInstance().getDialogManager() != null) {
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            SAMP.getInstance().getDialogManager().SendDialogResponseWithId(1, 1100, 0, pass);
                        }
                    });
                }
                hideBrowser();
            } else if ("cef:onRegisterSubmit".equals(event)) {
                final String pass = extractFirstArg(jsonArgs);
                if (SAMP.getInstance() != null && SAMP.getInstance().getDialogManager() != null) {
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            SAMP.getInstance().getDialogManager().SendDialogResponseWithId(1, 1101, 0, pass);
                        }
                    });
                }
                hideBrowser();
            } else if ("cef:selectSchool".equals(event) || "cef:onSelectSchool".equals(event)) {
                final String school = extractFirstArg(jsonArgs);
                int item = 0;
                if ("2".equals(school) || "itr".equalsIgnoreCase(school) || "inthar".equalsIgnoreCase(school)) item = 1;
                else if ("3".equals(school) || "pcc".equalsIgnoreCase(school) || "prachachuen".equalsIgnoreCase(school)) item = 2;
                else if ("4".equals(school) || "brp".equalsIgnoreCase(school) || "buranapon".equalsIgnoreCase(school)) item = 3;
                final int listitem = item;

                if (SAMP.getInstance() != null && SAMP.getInstance().getDialogManager() != null) {
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            SAMP.getInstance().getDialogManager().SendDialogResponseWithId(1, 1099, listitem, school);
                        }
                    });
                }
                hideBrowser();
            } else if ("cef:closeWheel".equals(event)) {
                hideBrowser();
                if (SAMP.getInstance() != null) {
                    SAMP.getInstance().sendChatCommand("/closewheel");
                }
            } else if ("cef:closeCard".equals(event)) {
                hideBrowser();
                if (SAMP.getInstance() != null) {
                    SAMP.getInstance().sendChatCommand("/closecard");
                }
            } else if ("cef:saveCardPhoto".equals(event)) {
                String photoUrl = extractFirstArg(jsonArgs);
                if (SAMP.getInstance() != null && photoUrl != null && !photoUrl.isEmpty()) {
                    SAMP.getInstance().sendChatCommand("/setcardphoto " + photoUrl);
                }
            } else if ("cef:exitAFK".equals(event)) {
                hideBrowser();
                if (SAMP.getInstance() != null) {
                    SAMP.getInstance().sendChatCommand("/exitafk");
                }
            } else if ("cef:closeSchool".equals(event)) {
                if (SAMP.getInstance() != null && SAMP.getInstance().getDialogManager() != null) {
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            SAMP.getInstance().getDialogManager().SendDialogResponseWithId(0, 1099, 0, "");
                        }
                    });
                }
                hideBrowser();
                if (SAMP.getInstance() != null) {
                    SAMP.getInstance().sendChatCommand("/closeschool");
                }
            } else if ("cef:closeUI".equals(event) || "cef:onCloseUI".equals(event) || "cef:closeInventory".equals(event)) {
                if (SAMP.getInstance() != null && SAMP.getInstance().getDialogManager() != null) {
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            SAMP.getInstance().getDialogManager().SendDialogResponseWithId(0, 1099, 0, "");
                        }
                    });
                }
                hideBrowser();
                if (SAMP.getInstance() != null) {
                    SAMP.getInstance().sendChatCommand("/closeinv");
                }
            } else if ("cef:wheelAction".equals(event)) {
                String action = extractFirstArg(jsonArgs);
                hideBrowser();
                if (SAMP.getInstance() != null) {
                    SAMP.getInstance().sendChatCommand("/closewheel");
                }
                if ("inventory".equalsIgnoreCase(action)) {
                    if (SAMP.getInstance() != null) {
                        SAMP.getInstance().sendChatCommand("/inv");
                    }
                }
            } else if ("cef:useItem".equals(event)) {
                String slot = extractFirstArg(jsonArgs);
                hideBrowser();
                if (SAMP.getInstance() != null) {
                    SAMP.getInstance().sendChatCommand("/useitem " + slot);
                }
            } else if ("cef:dropItem".equals(event)) {
                String data = extractFirstArg(jsonArgs);
                hideBrowser();
                if (SAMP.getInstance() != null) {
                    String[] parts = data.split(":");
                    if (parts.length >= 2) {
                        SAMP.getInstance().sendChatCommand("/dropitem " + parts[0] + " " + parts[1]);
                    }
                }
            } else if ("cef:giveItem".equals(event)) {
                String data = extractFirstArg(jsonArgs);
                hideBrowser();
                if (SAMP.getInstance() != null) {
                    String[] parts = data.split(":");
                    if (parts.length >= 3) {
                        SAMP.getInstance().sendChatCommand("/senditem " + parts[0] + " " + parts[1] + " " + parts[2]);
                    }
                }
            } else if ("cef:closeCrafting".equals(event)) {
                hideBrowser();
                if (SAMP.getInstance() != null) {
                    SAMP.getInstance().sendChatCommand("/closecraft");
                }
            } else if ("cef:craftItem".equals(event)) {
                String recipeId = extractFirstArg(jsonArgs);
                if (SAMP.getInstance() != null) {
                    SAMP.getInstance().sendChatCommand("/docraft " + recipeId);
                }
            } else if ("cef:runCommand".equals(event)) {
                String cmd = extractFirstArg(jsonArgs);
                if (SAMP.getInstance() != null && cmd != null && !cmd.isEmpty()) {
                    SAMP.getInstance().sendChatCommand(cmd);
                }
            } else if ("cef:requestSchoolRespawn".equals(event)) {
                hideBrowser();
                if (SAMP.getInstance() != null) {
                    SAMP.getInstance().sendChatCommand("/respawn");
                }
            } else if ("cef:closeGarage".equals(event)) {
                hideBrowser();
                if (SAMP.getInstance() != null) {
                    SAMP.getInstance().sendChatCommand("/closegarage");
                }
            } else if ("cef:spawnGarageVehicle".equals(event)) {
                String vehId = extractFirstArg(jsonArgs);
                hideBrowser();
                if (SAMP.getInstance() != null) {
                    SAMP.getInstance().sendChatCommand("/spawnveh " + vehId);
                }
            } else if ("cef:storeGarageVehicle".equals(event)) {
                String vehId = extractFirstArg(jsonArgs);
                hideBrowser();
                if (SAMP.getInstance() != null) {
                    SAMP.getInstance().sendChatCommand("/storeveh " + vehId);
                }
            } else if ("cef:reclaimGarageVehicle".equals(event)) {
                String vehId = extractFirstArg(jsonArgs);
                hideBrowser();
                if (SAMP.getInstance() != null) {
                    SAMP.getInstance().sendChatCommand("/reclaimveh " + vehId);
                }
            }
        }




        private String extractFirstArg(String jsonArgs) {
            if (jsonArgs == null || jsonArgs.isEmpty()) return "";
            try {
                org.json.JSONArray arr = new org.json.JSONArray(jsonArgs);
                if (arr.length() > 0) {
                    return String.valueOf(arr.get(0));
                }
            } catch (Exception ignored) {}
            String trimmed = jsonArgs.trim();
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
                return trimmed.substring(1, trimmed.length() - 1);
            }
            return trimmed;
        }
    }
}
