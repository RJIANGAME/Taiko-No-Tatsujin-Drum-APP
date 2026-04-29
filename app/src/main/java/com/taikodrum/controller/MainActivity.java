package com.taikodrum.controller;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.util.Locale;
import java.util.Random;

public final class MainActivity extends Activity {
    private static final String PREFS = "taiko_drum_settings";
    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";
    private static final String KEY_TOKEN = "token";

    private final DrumClient client = new DrumClient();
    private DrumPadView drumPadView;
    private EditText hostEdit;
    private EditText portEdit;
    private EditText tokenEdit;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        client.setStatusListener((message, error) -> {
            statusText.setText(message);
            statusText.setTextColor(error ? Color.rgb(255, 112, 96) : Color.rgb(142, 255, 177));
            statusText.setBackground(statusBackground(error));
        });

        setContentView(createContentView());
        enterFullscreen();
        loadSettings();
        applySettings(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (drumPadView != null) {
            drumPadView.releaseAll();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterFullscreen();
        }
    }

    @Override
    protected void onDestroy() {
        client.close();
        super.onDestroy();
    }

    private View createContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(panelBackground(Color.rgb(80, 18, 14), Color.rgb(23, 9, 10), dp(0), 0));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(dp(10), dp(8), dp(10), dp(8));
        controls.setBackground(panelBackground(Color.rgb(145, 38, 20), Color.rgb(57, 19, 16), dp(0), 0));

        LinearLayout titleGroup = new LinearLayout(this);
        titleGroup.setOrientation(LinearLayout.VERTICAL);
        titleGroup.setGravity(Gravity.CENTER);
        TextView title = label("TAIKO", 22, Color.rgb(255, 239, 174));
        TextView subtitle = label("PHONE DRUM", 10, Color.rgb(255, 184, 82));
        titleGroup.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        titleGroup.addView(subtitle, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        controls.addView(titleGroup, params(dp(132), LinearLayout.LayoutParams.MATCH_PARENT, 0f, 0, 0, 10, 0));

        hostEdit = edit("PC IP");
        hostEdit.setInputType(InputType.TYPE_CLASS_PHONE);
        controls.addView(hostEdit, params(0, dp(46), 1.5f, 0, 0, 8, 0));

        portEdit = edit("Port");
        portEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        portEdit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});
        controls.addView(portEdit, params(dp(82), dp(46), 0f, 0, 0, 8, 0));

        tokenEdit = edit("Token");
        tokenEdit.setSingleLine(true);
        tokenEdit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(16)});
        controls.addView(tokenEdit, params(dp(116), dp(46), 0f, 0, 0, 10, 0));

        Button wifi = button("Wi-Fi");
        wifi.setOnClickListener(v -> applySettings(true));
        controls.addView(wifi, params(dp(76), dp(46), 0f, 0, 0, 7, 0));

        Button usb = button("USB");
        usb.setOnClickListener(v -> {
            hostEdit.setText("127.0.0.1");
            applySettings(true);
        });
        controls.addView(usb, params(dp(66), dp(46), 0f, 0, 0, 7, 0));

        Button test = button("Test D");
        test.setOnClickListener(v -> {
            if (applySettings(true)) {
                client.sendTap("D");
            }
        });
        controls.addView(test, params(dp(84), dp(46), 0f, 0, 0, 10, 0));

        statusText = label("Ready", 11, Color.rgb(142, 255, 177));
        statusText.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        statusText.setSingleLine(true);
        statusText.setPadding(dp(10), 0, dp(10), 0);
        statusText.setBackground(statusBackground(false));
        controls.addView(statusText, params(0, dp(46), 1.1f, 0, 0, 0, 0));

        drumPadView = new DrumPadView(this);
        drumPadView.setHitListener((zone, pressed) -> client.sendKey(zone.key, pressed));

        root.addView(controls, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(72)));
        root.addView(drumPadView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        hostEdit.setText(prefs.getString(KEY_HOST, "127.0.0.1"));
        portEdit.setText(String.format(Locale.US, "%d", prefs.getInt(KEY_PORT, DrumClient.DEFAULT_PORT)));

        String token = prefs.getString(KEY_TOKEN, null);
        if (token == null || token.trim().isEmpty()) {
            token = generateToken();
            prefs.edit().putString(KEY_TOKEN, token).apply();
        }
        tokenEdit.setText(token);
    }

    private boolean applySettings(boolean save) {
        hideKeyboard();
        int port;
        try {
            port = Integer.parseInt(portEdit.getText().toString().trim());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            statusText.setText("Bad port");
            statusText.setTextColor(Color.rgb(255, 112, 96));
            return false;
        }

        String host = hostEdit.getText().toString().trim();
        String token = tokenEdit.getText().toString().trim().replace("|", "");
        if (!token.equals(tokenEdit.getText().toString().trim())) {
            tokenEdit.setText(token);
        }
        try {
            client.configure(host, port, token);
        } catch (IOException ex) {
            statusText.setText(ex.getMessage());
            statusText.setTextColor(Color.rgb(255, 112, 96));
            return false;
        }

        if (save) {
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_HOST, host)
                    .putInt(KEY_PORT, port)
                    .putString(KEY_TOKEN, token)
                    .apply();
        }
        return true;
    }

    @SuppressWarnings("deprecation")
    private void enterFullscreen() {
        View decorView = getWindow().getDecorView();
        if (decorView == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = decorView.getWindowInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            enterLegacyFullscreen();
        }
    }

    @SuppressWarnings("deprecation")
    private void enterLegacyFullscreen() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view == null) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
        view.clearFocus();
    }

    private TextView label(String text, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(sp);
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private EditText edit(String hint) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setSingleLine(true);
        edit.setTextColor(Color.WHITE);
        edit.setHintTextColor(Color.rgb(162, 140, 132));
        edit.setTextSize(14);
        edit.setSelectAllOnFocus(true);
        edit.setPadding(dp(10), 0, dp(10), 0);
        edit.setBackground(editBackground());
        return edit;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(13);
        button.setTextColor(Color.WHITE);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setBackground(buttonBackground());
        return button;
    }

    private LinearLayout.LayoutParams params(int width, int height, float weight, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height, weight);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private GradientDrawable panelBackground(int topColor, int bottomColor, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{topColor, bottomColor}
        );
        drawable.setCornerRadius(radius);
        if (strokeColor != 0) {
            drawable.setStroke(dp(2), strokeColor);
        }
        return drawable;
    }

    private GradientDrawable editBackground() {
        GradientDrawable drawable = panelBackground(Color.rgb(52, 31, 25), Color.rgb(33, 21, 19), dp(12), Color.rgb(236, 167, 74));
        return drawable;
    }

    private GradientDrawable buttonBackground() {
        return panelBackground(Color.rgb(239, 84, 33), Color.rgb(151, 42, 22), dp(14), Color.rgb(255, 224, 144));
    }

    private GradientDrawable statusBackground(boolean error) {
        int stroke = error ? Color.rgb(255, 118, 91) : Color.rgb(107, 231, 139);
        int top = error ? Color.rgb(62, 22, 20) : Color.rgb(21, 47, 31);
        int bottom = error ? Color.rgb(38, 15, 15) : Color.rgb(15, 31, 22);
        return panelBackground(top, bottom, dp(12), stroke);
    }

    private String generateToken() {
        int value = 100000 + new Random().nextInt(900000);
        return String.format(Locale.US, "%06d", value);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
