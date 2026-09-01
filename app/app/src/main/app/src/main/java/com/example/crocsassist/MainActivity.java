package com.example.crocsassist;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private WebView webView;
    private EditText urlInput, sizeInput;
    private CheckBox autoBag;
    private TextView statusText;
    private final Handler handler = new Handler();
    private boolean running = false;
    private static final long CHECK_MS = 12000;
    private SharedPreferences prefs;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        urlInput = findViewById(R.id.urlInput);
        sizeInput = findViewById(R.id.sizeInput);
        autoBag = findViewById(R.id.autoBag);
        statusText = findViewById(R.id.statusText);
        webView = findViewById(R.id.webView);

        Button start = findViewById(R.id.startButton);
        Button stop = findViewById(R.id.stopButton);
        Button cart = findViewById(R.id.cartButton);

        prefs = getSharedPreferences("settings", MODE_PRIVATE);

        urlInput.setText(
                prefs.getString("url", "https://www.crocs.com/")
        );

        sizeInput.setText(
                prefs.getString("size", "")
        );

        autoBag.setChecked(
                prefs.getBoolean("autoBag", false)
        );

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        webView.addJavascriptInterface(
                new JsBridge(),
                "AndroidBridge"
        );

        webView.setWebChromeClient(new WebChromeClient());

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                statusText.setText("Loaded. Checking page...");

                if (running) {
                    handler.postDelayed(checkRunnable, 1500);
                }
            }
        });

        createNotificationChannel();

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(
                        Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{
                            Manifest.permission.POST_NOTIFICATIONS
                    },
                    9
            );
        }

        start.setOnClickListener(v -> startMonitoring());

        stop.setOnClickListener(v -> stopMonitoring());

        cart.setOnClickListener(
                v -> webView.loadUrl(
                        "https://www.crocs.com/cart"
                )
        );
    }

    private void startMonitoring() {

        String url =
                urlInput.getText().toString().trim();

        if (!url.startsWith("https://www.crocs.com/") &&
                !url.startsWith("https://crocs.com/")) {

            Toast.makeText(
                    this,
                    "Use a crocs.com product URL",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        prefs.edit()
                .putString("url", url)
                .putString(
                        "size",
                        sizeInput.getText()
                                .toString()
                                .trim()
                )
                .putBoolean(
                        "autoBag",
                        autoBag.isChecked()
                )
                .apply();

        running = true;

        statusText.setText(
                "Monitoring while this app is open..."
        );

        webView.loadUrl(url);
    }

    private void stopMonitoring() {

        running = false;

        handler.removeCallbacks(
                checkRunnable
        );

        statusText.setText("Stopped");
    }

    private final Runnable checkRunnable =
            new Runnable() {

                @Override
                public void run() {

                    if (!running) return;

                    String wanted =
                            sizeInput
                                    .getText()
                                    .toString()
                                    .trim()
                                    .replace("\\", "\\\\")
                                    .replace("'", "\\'");

                    boolean clickBag =
                            autoBag.isChecked();

                    String js =
                            "(function(){" +
                            "const want='" +
                            wanted.toLowerCase() +
                            "';" +

                            "const items=[...document.querySelectorAll(" +
                            "'button,[role=button],label,option')];" +

                            "let sizeEl=items.find(e=>" +
                            "((e.innerText||e.textContent||'')" +
                            ".trim().toLowerCase()===want));" +

                            "if(!sizeEl && want){" +
                            "sizeEl=items.find(e=>" +
                            "((e.innerText||e.textContent||'')" +
                            ".toLowerCase().includes(want)));" +
                            "}" +

                            "let selectable=false;" +

                            "if(sizeEl){" +
                            "const dis=sizeEl.disabled || " +
                            "sizeEl.getAttribute('aria-disabled')==='true' || " +
                            "/sold|unavailable/i.test(sizeEl.innerText||'');" +
                            "selectable=!dis;" +
                            "if(selectable) sizeEl.click();" +
                            "}" +

                            "const buttons=[...document.querySelectorAll(" +
                            "'button,[role=button]')];" +

                            "let bag=buttons.find(e=>" +
                            "/add to (bag|cart)/i.test(" +
                            "(e.innerText||e.textContent||'').trim()) && " +
                            "!e.disabled && " +
                            "e.getAttribute('aria-disabled')!=='true');" +

                            "if(selectable && bag){" +

                            (clickBag
                                    ? "bag.click();"
                                    : "") +

                            "AndroidBridge.found(" +
                            "'Size appears available');" +

                            "return;" +
                            "}" +

                            "AndroidBridge.status(" +
                            "sizeEl ? " +
                            "'Size found but not purchasable yet' : " +
                            "'Checking for target size...');" +

                            "})();";

                    webView.evaluateJavascript(
                            js,
                            null
                    );

                    handler.postDelayed(
                            this,
                            CHECK_MS
                    );
                }
            };

    public class JsBridge {

        @JavascriptInterface
        public void found(String msg) {

            runOnUiThread(() -> {

                statusText.setText(msg);

                notifyFound(msg);

                running = false;

                handler.removeCallbacks(
                        checkRunnable
                );
            });
        }

        @JavascriptInterface
        public void status(String msg) {

            runOnUiThread(
                    () -> statusText.setText(msg)
            );
        }
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            NotificationChannel channel =
                    new NotificationChannel(
                            "stock",
                            "Crocs availability",
                            NotificationManager.IMPORTANCE_HIGH
                    );

            getSystemService(
                    NotificationManager.class
            ).createNotificationChannel(channel);
        }
    }

    private void notifyFound(String msg) {

        Intent intent =
                new Intent(
                        this,
                        MainActivity.class
                );

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT |
                        PendingIntent.FLAG_IMMUTABLE
                );

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= 
