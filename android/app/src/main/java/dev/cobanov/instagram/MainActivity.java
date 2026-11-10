package dev.cobanov.instagram;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.provider.OpenableColumns;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {

    private static final int FILE_CHOOSER_RESULT_CODE = 1001;
    private static final String INSTAGRAM_HOME = "https://www.instagram.com/";
    private static final int COLOR_BG = Color.parseColor("#141210");
    private static final int COLOR_SURFACE = Color.parseColor("#1c1a17");
    private static final int COLOR_SURFACE_2 = Color.parseColor("#262320");
    private static final int COLOR_LINE = Color.parseColor("#2b2823");
    private static final int COLOR_INK = Color.parseColor("#f5f2ed");
    private static final int COLOR_MUTED = Color.parseColor("#a09a90");
    private static final int COLOR_ACCENT = Color.parseColor("#e56192");

    private static final Pattern FOLLOW_FILE_PATTERN =
            Pattern.compile("^(followers|following)(_\\d+)?\\.(json|html)$", Pattern.CASE_INSENSITIVE);

    private static final String MOBILE_PANEL_CSS =
            "(function(){" +
            "if(document.getElementById('iu-android-mobile-css'))return;" +
            "var s=document.createElement('style');" +
            "s.id='iu-android-mobile-css';" +
            "s.textContent='@media (max-width: 768px){#iu-app .iu-panel{width:calc(100vw - 16px)!important;max-height:calc(100dvh - 16px)!important;left:8px!important;right:8px!important;top:8px!important;bottom:8px!important;}#iu-app .iu-list{max-height:none!important;flex:1 1 auto!important;}}';" +
            "document.head.appendChild(s);" +
            "})();";

    private WebView igWebView;
    private WebView exportWebView;
    private ProgressBar progressBar;
    private TextView tabIgBtn;
    private TextView tabExportBtn;
    private TextView panelActionBtn;
    private TextView reloadBtn;
    private TextView langBtn;

    private ValueCallback<Uri[]> filePathCallback;
    private boolean isExportTab = false;
    private boolean pendingPanelOpen = false;
    private String pendingUnfollowList = null;
    private String unfollowerScript = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(COLOR_BG);
        getWindow().setNavigationBarColor(COLOR_BG);

        unfollowerScript = readAssetText("instagram-unfollower.js");
        String exportHtml = readAssetText("export-app.html");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        View topBar = buildTopBar();
        root.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        View divider = new View(this);
        divider.setBackgroundColor(COLOR_LINE);
        root.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

        FrameLayout contentFrame = new FrameLayout(this);
        contentFrame.setBackgroundColor(COLOR_BG);

        igWebView = createInstagramWebView();
        exportWebView = createExportWebView(exportHtml);

        contentFrame.addView(igWebView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        contentFrame.addView(exportWebView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(contentFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        switchTab(false);
        igWebView.loadUrl(INSTAGRAM_HOME);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(COLOR_BG);
        bar.setPadding(dp(10), dp(6), dp(10), dp(6));

        LinearLayout tabGroup = new LinearLayout(this);
        tabGroup.setOrientation(LinearLayout.HORIZONTAL);
        tabGroup.setGravity(Gravity.CENTER_VERTICAL);
        tabGroup.setPadding(dp(3), dp(3), dp(3), dp(3));
        tabGroup.setBackground(pillDrawable(COLOR_SURFACE, COLOR_LINE, dp(999)));

        tabIgBtn = createButtonLabel("Canlı Panel", 13f, true);
        tabIgBtn.setPadding(dp(12), dp(6), dp(12), dp(6));
        tabIgBtn.setOnClickListener(v -> switchTab(false));

        tabExportBtn = createButtonLabel("ZIP Analiz", 13f, false);
        tabExportBtn.setPadding(dp(12), dp(6), dp(12), dp(6));
        tabExportBtn.setOnClickListener(v -> switchTab(true));

        tabGroup.addView(tabIgBtn);
        tabGroup.addView(tabExportBtn);

        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout leftWrap = new LinearLayout(this);
        leftWrap.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        leftWrap.addView(tabGroup);
        bar.addView(leftWrap, tabLp);

        LinearLayout rightActions = new LinearLayout(this);
        rightActions.setOrientation(LinearLayout.HORIZONTAL);
        rightActions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        reloadBtn = createButtonLabel("↻", 15f, false);
        reloadBtn.setPadding(dp(11), dp(5), dp(11), dp(5));
        reloadBtn.setBackground(pillDrawable(COLOR_SURFACE, COLOR_LINE, dp(999)));
        reloadBtn.setTextColor(COLOR_MUTED);
        LinearLayout.LayoutParams reloadLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        reloadLp.rightMargin = dp(6);
        reloadBtn.setOnClickListener(v -> {
            if (igWebView != null) {
                igWebView.reload();
            }
        });
        rightActions.addView(reloadBtn, reloadLp);

        panelActionBtn = createButtonLabel("✦ Paneli Aç", 13f, true);
        panelActionBtn.setPadding(dp(14), dp(7), dp(14), dp(7));
        panelActionBtn.setBackground(pillDrawable(COLOR_ACCENT, COLOR_ACCENT, dp(999)));
        panelActionBtn.setTextColor(COLOR_BG);
        panelActionBtn.setOnClickListener(v -> toggleOrOpenPanel());
        rightActions.addView(panelActionBtn);

        langBtn = createButtonLabel("TR / EN", 12f, false);
        langBtn.setPadding(dp(12), dp(6), dp(12), dp(6));
        langBtn.setBackground(pillDrawable(COLOR_SURFACE, COLOR_LINE, dp(999)));
        langBtn.setTextColor(COLOR_INK);
        langBtn.setVisibility(View.GONE);
        langBtn.setOnClickListener(v -> {
            if (exportWebView != null) {
                exportWebView.evaluateJavascript(
                        "document.querySelector('[data-language-toggle]')?.click();", null);
            }
        });
        rightActions.addView(langBtn);

        bar.addView(rightActions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return bar;
    }

    private TextView createButtonLabel(String text, float sp, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        return tv;
    }

    private GradientDrawable pillDrawable(int fillColor, int strokeColor, int radiusPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fillColor);
        gd.setCornerRadius(radiusPx);
        gd.setStroke(dp(1), strokeColor);
        return gd;
    }

    private void switchTab(boolean showExport) {
        isExportTab = showExport;
        if (igWebView != null && exportWebView != null) {
            igWebView.setVisibility(showExport ? View.GONE : View.VISIBLE);
            exportWebView.setVisibility(showExport ? View.VISIBLE : View.GONE);
        }
        if (showExport) {
            tabExportBtn.setBackground(pillDrawable(COLOR_SURFACE_2, COLOR_ACCENT, dp(999)));
            tabExportBtn.setTextColor(COLOR_INK);
            tabIgBtn.setBackgroundColor(Color.TRANSPARENT);
            tabIgBtn.setTextColor(COLOR_MUTED);
            panelActionBtn.setVisibility(View.GONE);
            reloadBtn.setVisibility(View.GONE);
            langBtn.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
        } else {
            tabIgBtn.setBackground(pillDrawable(COLOR_SURFACE_2, COLOR_ACCENT, dp(999)));
            tabIgBtn.setTextColor(COLOR_INK);
            tabExportBtn.setBackgroundColor(Color.TRANSPARENT);
            tabExportBtn.setTextColor(COLOR_MUTED);
            panelActionBtn.setVisibility(View.VISIBLE);
            reloadBtn.setVisibility(View.VISIBLE);
            langBtn.setVisibility(View.GONE);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createInstagramWebView() {
        WebView webView = new WebView(this);
        webView.setBackgroundColor(COLOR_BG);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        String defaultUa = settings.getUserAgentString();
        if (defaultUa != null) {
            String cleanUa = defaultUa.replace("; wv)", ")").replace("Version/4.0 ", "");
            settings.setUserAgentString(cleanUa);
        }

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimeType, long contentLength) {
                enqueueDownload(url, userAgent, contentDisposition, mimeType);
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri == null) return false;
                String scheme = uri.getScheme();
                if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) {
                    return false;
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                CookieManager.getInstance().flush();
                if (url != null && Uri.parse(url).getHost() != null
                        && "www.instagram.com".equalsIgnoreCase(Uri.parse(url).getHost())) {
                    if (pendingUnfollowList != null) {
                        String listToLoad = pendingUnfollowList;
                        pendingUnfollowList = null;
                        pendingPanelOpen = false;
                        injectPanelWithList(listToLoad);
                    } else if (pendingPanelOpen) {
                        pendingPanelOpen = false;
                        injectUnfollowerPanel();
                    }
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (isExportTab) return;
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                                          boolean isUserGesture, Message resultMsg) {
                if (!isUserGesture || resultMsg == null) return false;
                WebView transportWebView = showModalProfileWebView(null);
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(transportWebView);
                resultMsg.sendToTarget();
                return true;
            }
        });

        return webView;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createExportWebView(String exportHtml) {
        WebView webView = new WebView(this);
        webView.setBackgroundColor(COLOR_BG);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri == null) return true;
                String url = uri.toString();

                if ("app://unfollow-from-export".equals(url)) {
                    view.evaluateJavascript("window.__iuExportUsernames || ''", rawJson -> {
                        String usernames = decodeJsString(rawJson);
                        if (!usernames.trim().isEmpty()) {
                            openPanelWithExportList(usernames);
                        }
                    });
                    return true;
                }

                if ("app://copy-clipboard".equals(url)) {
                    view.evaluateJavascript("window.__iuClipboardText || ''", rawJson -> {
                        String text = decodeJsString(rawJson);
                        if (!text.isEmpty()) {
                            copyToClipboard(text, true);
                        }
                    });
                    return true;
                }

                String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
                if (host.contains("accountscenter.instagram.com")) {
                    switchTab(false);
                    igWebView.loadUrl(url);
                    Toast.makeText(MainActivity.this,
                            "Instagram veri indirme sayfası açılıyor...",
                            Toast.LENGTH_SHORT).show();
                    return true;
                }

                if (host.contains("instagram.com")) {
                    showModalProfileWebView(url);
                    return true;
                }

                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;

                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

                try {
                    startActivityForResult(
                            Intent.createChooser(intent, "Instagram ZIP / JSON Dosyası Seç"),
                            FILE_CHOOSER_RESULT_CODE);
                } catch (ActivityNotFoundException e) {
                    MainActivity.this.filePathCallback = null;
                    Toast.makeText(MainActivity.this,
                            "Dosya seçici açılamadı.", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });

        webView.loadDataWithBaseURL("https://export.local/", exportHtml, "text/html", "UTF-8", null);
        return webView;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView showModalProfileWebView(String initialUrl) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(COLOR_BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(COLOR_BG);
        header.setPadding(dp(14), dp(8), dp(10), dp(8));

        TextView titleView = createButtonLabel("Instagram Profil", 14f, true);
        titleView.setTextColor(COLOR_INK);
        titleView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        header.addView(titleView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        WebView modalWebView = new WebView(this);
        modalWebView.setBackgroundColor(COLOR_BG);

        TextView openNativeBtn = createButtonLabel("Uygulamada Aç", 12f, false);
        openNativeBtn.setPadding(dp(12), dp(6), dp(12), dp(6));
        openNativeBtn.setBackground(pillDrawable(COLOR_SURFACE, COLOR_LINE, dp(999)));
        openNativeBtn.setTextColor(COLOR_INK);
        LinearLayout.LayoutParams nativeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nativeLp.rightMargin = dp(8);
        openNativeBtn.setOnClickListener(v -> {
            String currentUrl = modalWebView.getUrl();
            if (currentUrl != null && currentUrl.startsWith("https://")) {
                try {
                    Intent viewIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl));
                    startActivity(viewIntent);
                } catch (ActivityNotFoundException ignored) {
                }
            }
        });
        header.addView(openNativeBtn, nativeLp);

        TextView closeBtn = createButtonLabel("✕ Kapat", 12f, true);
        closeBtn.setPadding(dp(12), dp(6), dp(12), dp(6));
        closeBtn.setBackground(pillDrawable(COLOR_ACCENT, COLOR_ACCENT, dp(999)));
        closeBtn.setTextColor(COLOR_BG);
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        header.addView(closeBtn);

        container.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        View divider = new View(this);
        divider.setBackgroundColor(COLOR_LINE);
        container.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(modalWebView, true);

        WebSettings s = modalWebView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        if (igWebView != null) {
            s.setUserAgentString(igWebView.getSettings().getUserAgentString());
        }

        modalWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (u == null) return true;
                String scheme = u.getScheme();
                return !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (url != null) {
                    Uri u = Uri.parse(url);
                    String path = u.getPath();
                    if (path != null && path.length() > 1) {
                        titleView.setText("@" + path.replace("/", ""));
                    }
                }
            }
        });

        container.addView(modalWebView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        dialog.setContentView(container);
        dialog.setOnDismissListener(d -> {
            modalWebView.stopLoading();
            modalWebView.destroy();
        });
        dialog.show();

        if (initialUrl != null && !initialUrl.isEmpty()) {
            modalWebView.loadUrl(initialUrl);
        }
        return modalWebView;
    }

    private void toggleOrOpenPanel() {
        switchTab(false);
        String currentUrl = igWebView.getUrl();
        String host = currentUrl != null ? Uri.parse(currentUrl).getHost() : null;
        if (host == null || !"www.instagram.com".equalsIgnoreCase(host)) {
            pendingPanelOpen = true;
            Toast.makeText(this,
                    "Instagram açılıyor, sayfa yüklenince panel açılacak...",
                    Toast.LENGTH_SHORT).show();
            igWebView.loadUrl(INSTAGRAM_HOME);
            return;
        }

        String checkStateJs =
                "(function(){" +
                "var app=document.getElementById('iu-app');" +
                "if(!app)return 'missing';" +
                "var exp=app.querySelector('[data-action=\"expand\"]');" +
                "if(exp){exp.click();return 'expanded';}" +
                "if(typeof window.__iuCleanup==='function'){window.__iuCleanup();}else{document.dispatchEvent(new Event('iu-cleanup'));}" +
                "return 'closed';" +
                "})();";

        igWebView.evaluateJavascript(checkStateJs, rawResult -> {
            String state = decodeJsString(rawResult);
            if ("missing".equals(state)) {
                injectUnfollowerPanel();
            }
        });
    }

    private void injectUnfollowerPanel() {
        if (unfollowerScript == null || unfollowerScript.isEmpty()) {
            Toast.makeText(this, "Panel betiği yüklenemedi.", Toast.LENGTH_SHORT).show();
            return;
        }
        igWebView.evaluateJavascript(unfollowerScript + "\n" + MOBILE_PANEL_CSS, null);
    }

    private void openPanelWithExportList(String usernames) {
        copyToClipboard(usernames, false);
        switchTab(false);

        String currentUrl = igWebView.getUrl();
        String host = currentUrl != null ? Uri.parse(currentUrl).getHost() : null;
        if (host == null || !"www.instagram.com".equalsIgnoreCase(host)) {
            pendingUnfollowList = usernames;
            Toast.makeText(this,
                    "Instagram açılıyor, liste panele aktarılacak...",
                    Toast.LENGTH_SHORT).show();
            igWebView.loadUrl(INSTAGRAM_HOME);
            return;
        }

        injectPanelWithList(usernames);
    }

    private void injectPanelWithList(String usernames) {
        if (unfollowerScript == null || unfollowerScript.isEmpty()) return;
        String quotedList = JSONObject.quote(usernames);
        String fillListJs =
                "(function(listText){" +
                "var app=document.getElementById('iu-app');" +
                "if(!app)return;" +
                "var pasteBtn=app.querySelector('[data-action=\"paste-list\"]');" +
                "if(pasteBtn){" +
                "pasteBtn.click();" +
                "var ta=app.querySelector('[data-list-input]');" +
                "if(ta){" +
                "ta.value=listText;" +
                "var confirmBtn=app.querySelector('.iu-dialog [data-confirm]');" +
                "if(confirmBtn)confirmBtn.click();" +
                "}" +
                "}" +
                "})(" + quotedList + ");";

        igWebView.evaluateJavascript(
                unfollowerScript + "\n" + MOBILE_PANEL_CSS + "\n" + fillListJs,
                null);
        Toast.makeText(this,
                "Veri dosyasındaki geri takip etmeyenler panele aktarıldı.",
                Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_RESULT_CODE) return;

        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }

        if (resultCode != Activity.RESULT_OK || data == null) {
            return;
        }

        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            ClipData clipData = data.getClipData();
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri u = clipData.getItemAt(i).getUri();
                if (u != null) uris.add(u);
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }

        if (uris.isEmpty()) return;

        exportWebView.evaluateJavascript(
                "if(window.__setAndroidBusy)window.__setAndroidBusy(true);", null);

        new Thread(() -> {
            JSONArray filesJson = extractSelectedFiles(uris);
            runOnUiThread(() -> {
                if (exportWebView != null) {
                    exportWebView.evaluateJavascript(
                            "if(window.__handleAndroidFiles)window.__handleAndroidFiles("
                                    + filesJson.toString() + ");",
                            null);
                }
            });
        }).start();
    }

    private JSONArray extractSelectedFiles(List<Uri> uris) {
        JSONArray result = new JSONArray();
        for (Uri uri : uris) {
            String displayName = normalizeFileName(getDisplayName(uri));
            try (InputStream rawIn = getContentResolver().openInputStream(uri)) {
                if (rawIn == null) continue;
                BufferedInputStream in = new BufferedInputStream(rawIn);
                in.mark(8);
                byte[] magic = new byte[4];
                int read = in.read(magic);
                in.reset();

                boolean isZipMagic = read == 4
                        && magic[0] == 0x50 && magic[1] == 0x4b
                        && magic[2] == 0x03 && magic[3] == 0x04;

                if (isZipMagic) {
                    boolean sawAnyEntry = false;
                    int matchedCount = 0;
                    try (ZipInputStream zis = new ZipInputStream(in, StandardCharsets.UTF_8)) {
                        ZipEntry entry;
                        while ((entry = zis.getNextEntry()) != null) {
                            sawAnyEntry = true;
                            if (entry.isDirectory()) {
                                zis.closeEntry();
                                continue;
                            }
                            String entryPath = entry.getName();
                            String baseName = normalizeFileName(baseNameOf(entryPath));
                            if (FOLLOW_FILE_PATTERN.matcher(baseName).matches()) {
                                String content = readStreamText(zis, 32 * 1024 * 1024);
                                JSONObject item = new JSONObject();
                                item.put("path", baseName);
                                item.put("text", content);
                                result.put(item);
                                matchedCount++;
                            }
                            zis.closeEntry();
                        }
                    }
                    if (!sawAnyEntry) {
                        JSONObject bad = new JSONObject();
                        bad.put("path", "invalid.zip");
                        bad.put("text", "not a zip");
                        result.put(bad);
                    } else if (matchedCount == 0) {
                        JSONObject unrelated = new JSONObject();
                        unrelated.put("path", "media/posts_1.json");
                        unrelated.put("text", "[]");
                        result.put(unrelated);
                    }
                } else if (FOLLOW_FILE_PATTERN.matcher(displayName).matches()) {
                    String content = readStreamText(in, 32 * 1024 * 1024);
                    JSONObject item = new JSONObject();
                    item.put("path", displayName);
                    item.put("text", content);
                    result.put(item);
                } else {
                    JSONObject notZip = new JSONObject();
                    notZip.put("path", "invalid.zip");
                    notZip.put("text", "not a zip");
                    result.put(notZip);
                }
            } catch (Exception e) {
                try {
                    JSONObject broken = new JSONObject();
                    broken.put("path", displayName.endsWith(".json") ? displayName : "invalid.zip");
                    broken.put("text", "{ broken");
                    result.put(broken);
                } catch (Exception ignored) {
                }
            }
        }
        return result;
    }

    private String getDisplayName(Uri uri) {
        String result = null;
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) {
                        result = cursor.getString(idx);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result != null ? result : "export.zip";
    }

    private static String baseNameOf(String path) {
        if (path == null) return "";
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String normalizeFileName(String name) {
        if (name == null) return "";
        // Strip duplicate download suffix like "followers_1 (1).json" -> "followers_1.json"
        return name.trim().replaceAll("\\s+\\(\\d+\\)(?=\\.(json|html|zip)$)", "");
    }

    private static String readStreamText(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        int total = 0;
        int n;
        while ((n = in.read(buffer)) != -1) {
            total += n;
            if (total > maxBytes) {
                throw new IOException("File exceeds maximum allowed size");
            }
            out.write(buffer, 0, n);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private void enqueueDownload(String url, String userAgent,
                                 String contentDisposition, String mimeType) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) {
                request.addRequestHeader("Cookie", cookies);
            }
            if (userAgent != null) {
                request.addRequestHeader("User-Agent", userAgent);
            }
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
            request.setTitle(fileName);
            request.setDescription("Instagram veri dosyası indiriliyor...");
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(request);
                Toast.makeText(this,
                        "İndirme başlatıldı: " + fileName,
                        Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "İndirme başlatılamadı.", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyToClipboard(String text, boolean showToast) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("Instagram Unfollowers", text);
            clipboard.setPrimaryClip(clip);
            if (showToast) {
                Toast.makeText(this, "Listeyi panoya kopyaladın.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String decodeJsString(String rawJson) {
        if (rawJson == null || "null".equals(rawJson) || "undefined".equals(rawJson)) {
            return "";
        }
        try {
            return new JSONArray("[" + rawJson + "]").optString(0, "");
        } catch (Exception e) {
            return "";
        }
    }

    private String readAssetText(String assetName) {
        try (InputStream in = getAssets().open(assetName)) {
            return readStreamText(in, 16 * 1024 * 1024);
        } catch (IOException e) {
            return "";
        }
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()));
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (isExportTab) {
            switchTab(false);
            return;
        }
        if (igWebView != null && igWebView.canGoBack()) {
            igWebView.goBack();
            return;
        }
        super.onBackPressed();
    }
}
