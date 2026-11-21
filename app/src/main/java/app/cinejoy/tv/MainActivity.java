// CineJoy TV app built by k10-ketan.
package app.cinejoy.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Set;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONException;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final String HOME = "https://cinejoy.pk/";
    private static final int BACKGROUND = 0xff101510;
    private static final int ACCENT = 0xff95ff50;

    private FrameLayout root;
    private WebView web;
    private LinearLayout error;
    private Cursor cursor;
    private View fullscreen;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private String remoteScript;
    private String mediaScript;
    private boolean frameControls;
    private boolean mediaFeedback;
    private int mediaRequest;
    private MediaSession mediaSession;
    private LinearLayout playbackControls;
    private int menuSelection;
    private String failedUrl;
    private int pageVersion;
    private boolean keyboardVisible;
    private boolean pointer;
    private boolean editing;
    private boolean leaving;
    private float cursorX;
    private float cursorY;
    private final Handler handler = new Handler();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        remoteScript = readScript("remote.js");
        mediaScript = readScript("media-controls.js");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root = new FrameLayout(this);
        root.setBackgroundColor(BACKGROUND);
        setContentView(root);
        createWebView();
        createMediaSession();
        cursor = new Cursor(this);
        cursor.setVisibility(View.GONE);
        root.addView(cursor, new FrameLayout.LayoutParams(-1, -1));
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            boolean visible = isKeyboardVisible();
            if (keyboardVisible && !visible) {
                editing = false;
                remote("finishEditing()", null);
            }
            keyboardVisible = visible;
        });
        applyImmersiveMode();
        if (state == null || web.restoreState(state) == null) web.loadUrl(HOME);
    }

    private boolean isKeyboardVisible() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsets insets = root.getRootWindowInsets();
            if (insets != null) return insets.isVisible(WindowInsets.Type.ime());
        }
        Point display = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(display);
        Rect frame = new Rect();
        root.getWindowVisibleDisplayFrame(frame);
        return display.y > 0 && frame.height() < display.y * 0.75f;
    }

    private void createWebView() {
        web = new WebView(this) {
            @Override public boolean onKeyPreIme(int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK && (editing || keyboardVisible)) {
                    if (event.getAction() == KeyEvent.ACTION_UP) finishEditing();
                    return true;
                }
                return super.onKeyPreIme(keyCode, event);
            }
        };
        web.setBackgroundColor(BACKGROUND);
        root.addView(web, 0, new FrameLayout.LayoutParams(-1, -1));
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        Set<String> cineJoyOrigins = new HashSet<>(Arrays.asList(
            "https://www.cinejoy.pk", "https://cinejoy.pk"));
        mediaFeedback = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER);
        if (mediaFeedback) {
            WebViewCompat.addWebMessageListener(web, "CineJoyTvMediaResult", cineJoyOrigins,
                (view, message, origin, isMainFrame, reply) -> {
                    if (isMainFrame && isCineJoy(view.getUrl())) showMediaResult(message.getData());
                });
        }
        frameControls = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT);
        if (frameControls) {
            WebViewCompat.addDocumentStartJavaScript(web, mediaScript, Collections.singleton("*"));
            WebViewCompat.addDocumentStartJavaScript(web, remoteScript, cineJoyOrigins);
        }
        web.setWebViewClient(new Browser());
        web.setWebChromeClient(new Chrome());
        web.setFocusableInTouchMode(true);
        web.requestFocus();
    }

    private String readScript(String name) {
        try (InputStream input = getAssets().open(name);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load the TV controls", exception);
        }
    }

    private static boolean isCineJoy(String url) {
        if (url == null) return false;
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        return "https".equalsIgnoreCase(uri.getScheme()) &&
            ("cinejoy.pk".equalsIgnoreCase(host) || "www.cinejoy.pk".equalsIgnoreCase(host));
    }

    private static boolean isWatchPath(String path) {
        return path != null && ("/watch".equals(path) || path.startsWith("/watch/"));
    }

    private boolean isWatching() {
        String url = web.getUrl();
        return isCineJoy(url) && isWatchPath(Uri.parse(url).getPath());
    }

    private void openExternal(Uri url) {
        String scheme = url.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, url));
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, "No browser is available to open this link", Toast.LENGTH_SHORT).show();
        }
    }

    private final class Browser extends WebViewClient {
        @Override public void doUpdateVisitedHistory(WebView view, String url, boolean reload) {
            if (mediaSession == null) return;
            boolean watching = isCineJoy(url) && isWatchPath(Uri.parse(url).getPath());
            mediaSession.setActive(watching);
            if (!watching) mediaRequest++;
        }

        @Override public void onPageCommitVisible(WebView view, String url) {
            if (isCineJoy(url)) installRemote();
        }

        @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (!request.isForMainFrame() || isCineJoy(request.getUrl().toString())) return false;
            if (request.hasGesture() && !isWatching()) openExternal(request.getUrl());
            return true;
        }

        @SuppressWarnings("deprecation")
        @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (isCineJoy(url)) return false;
            WebView.HitTestResult hit = view.getHitTestResult();
            if (!isWatching() && hit != null && (hit.getType() == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                    hit.getType() == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE)) {
                openExternal(Uri.parse(url));
            }
            return true;
        }

        @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            pageVersion++;
            mediaRequest++;
            hidePlaybackControls();
            editing = false;
            setPointer(false);
            if (error != null) {
                root.removeView(error);
                error = null;
            }
            failedUrl = null;
            if (isCineJoy(url)) installRemote();
        }

        @Override public void onPageFinished(WebView view, String url) {
            if (isCineJoy(url) && failedUrl == null) installRemote();
        }

        @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError failure) {
            if (request.isForMainFrame()) {
                showError(request.getUrl().toString());
            } else if (failure.getErrorCode() == ERROR_HOST_LOOKUP && isWatching()) {
                int version = pageVersion;
                remote("focusFailedServer(" + JSONObject.quote(request.getUrl().toString()) + ")", result -> {
                    if (version != pageVersion || !isWatching() || result == null || "null".equals(result)) return;
                    setPointer(false);
                    showMediaStatus("Server unavailable. Press Menu and choose Servers.");
                });
            }
        }

        @SuppressWarnings("deprecation")
        @Override public void onReceivedError(WebView view, int code, String description, String url) {
            if (Build.VERSION.SDK_INT < 23) showError(url);
        }

        @Override public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                                  WebResourceResponse response) {
            if (request.isForMainFrame() && response.getStatusCode() >= 500) {
                showError(request.getUrl().toString());
            }
        }
    }

    private void hideFullscreen() {
        if (fullscreen == null) return;
        root.removeView(fullscreen);
        fullscreen = null;
        setPointer(false);
        web.setVisibility(View.VISIBLE);
        web.requestFocus();
        WebChromeClient.CustomViewCallback callback = fullscreenCallback;
        fullscreenCallback = null;
        callback.onCustomViewHidden();
        applyImmersiveMode();
    }

    private final class Chrome extends WebChromeClient {
        @Override public void onShowCustomView(View view, CustomViewCallback callback) {
            if (fullscreen != null) {
                callback.onCustomViewHidden();
                return;
            }
            fullscreen = view;
            fullscreenCallback = callback;
            web.setVisibility(View.INVISIBLE);
            root.addView(view, root.indexOfChild(cursor), new FrameLayout.LayoutParams(-1, -1));
            view.requestFocus();
            applyImmersiveMode();
        }

        @Override public void onHideCustomView() {
            hideFullscreen();
        }

        @Override public boolean onCreateWindow(WebView view, boolean dialog, boolean userGesture, Message result) {
            // Embedded players can request a separate window when the viewer presses Play.
            if (!userGesture || isWatching()) return false;
            final WebView popup = new WebView(MainActivity.this);
            Runnable close = popup::destroy;
            handler.postDelayed(close, 5000);
            popup.setWebViewClient(new WebViewClient() {
                private boolean handled;

                private boolean forward(String url) {
                    if (handled || "about:blank".equals(url)) return true;
                    handled = true;
                    if (isCineJoy(url)) web.loadUrl(url);
                    else openExternal(Uri.parse(url));
                    handler.removeCallbacks(close);
                    handler.post(close);
                    return true;
                }

                @Override public boolean shouldOverrideUrlLoading(WebView popupView, WebResourceRequest request) {
                    return !request.isForMainFrame() || forward(request.getUrl().toString());
                }

                @SuppressWarnings("deprecation")
                @Override public boolean shouldOverrideUrlLoading(WebView popupView, String url) {
                    return forward(url);
                }
            });
            ((WebView.WebViewTransport) result.obj).setWebView(popup);
            result.sendToTarget();
            return true;
        }
    }

    private void showError(String url) {
        if (error != null) return;
        failedUrl = isCineJoy(url) ? url : HOME;
        setPointer(false);
        error = new LinearLayout(this);
        error.setOrientation(LinearLayout.VERTICAL);
        error.setGravity(Gravity.CENTER);
        error.setBackgroundColor(BACKGROUND);
        TextView message = new TextView(this);
        message.setText("CineJoy could not load");
        message.setTextColor(Color.WHITE);
        message.setTextSize(24);
        error.addView(message);
        Button retry = new Button(this);
        retry.setText("Retry");
        retry.setOnClickListener(view -> web.loadUrl(failedUrl));
        error.addView(retry);
        root.addView(error, new FrameLayout.LayoutParams(-1, -1));
        retry.requestFocus();
    }

    private void installRemote() {
        web.evaluateJavascript(frameControls ? remoteScript : remoteScript + mediaScript, null);
    }

    private void createMediaSession() {
        mediaSession = new MediaSession(this, "CineJoy TV");
        setMediaState(PlaybackState.STATE_PAUSED);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override public boolean onMediaButtonEvent(Intent intent) {
                KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (event != null && mediaAction(event.getKeyCode()) != null) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                        sendMediaAction(mediaAction(event.getKeyCode()), true);
                    }
                    return true;
                }
                return super.onMediaButtonEvent(intent);
            }
        });
    }

    private void setMediaState(int state) {
        mediaSession.setPlaybackState(new PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE |
                PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_REWIND | PlaybackState.ACTION_FAST_FORWARD)
            .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                state == PlaybackState.STATE_PLAYING ? 1f : 0f).build());
    }

    private void sendMediaAction(String action, boolean feedback) {
        if (!isCineJoy(web.getUrl())) return;
        String arguments = "'" + action + "'" + (feedback ? "," + ++mediaRequest : "");
        web.evaluateJavascript("if(window.cineJoyTvMedia) window.cineJoyTvMedia.command(" + arguments + ")", null);
        if (feedback && mediaFeedback) {
            int request = mediaRequest;
            handler.postDelayed(() -> {
                if (request == mediaRequest) showMediaStatus("Waiting for video");
            }, 1800);
        }
    }

    private void showMediaResult(String message) {
        try {
            JSONObject result = new JSONObject(message);
            if (result.getInt("id") != mediaRequest) return;
            mediaRequest++;
            switch (result.getString("outcome")) {
                case "playing":
                    setMediaState(PlaybackState.STATE_PLAYING);
                    showMediaStatus("Playing");
                    break;
                case "paused":
                    setMediaState(PlaybackState.STATE_PAUSED);
                    showMediaStatus("Paused");
                    break;
                case "back":
                    showMediaStatus("Back 10 seconds");
                    break;
                case "forward":
                    showMediaStatus("Forward 10 seconds");
                    break;
                case "unseekable":
                    showMediaStatus("Seeking is unavailable for this stream");
                    break;
                case "loading":
                    showMediaStatus("Waiting for video");
                    break;
                default:
                    showMediaStatus("No video ready");
            }
        } catch (JSONException ignored) {
            // Ignore malformed page messages.
        }
    }

    private void showMediaStatus(String text) {
        if (isWatching() || fullscreen != null) Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private static String mediaAction(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: return "toggle";
            case KeyEvent.KEYCODE_MEDIA_PLAY: return "play";
            case KeyEvent.KEYCODE_MEDIA_PAUSE: return "pause";
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD: return "back";
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD: return "forward";
            default: return null;
        }
    }

    private void showRemoteMenu() {
        setPointer(false);
        playbackControls = new LinearLayout(this);
        playbackControls.setGravity(Gravity.CENTER);
        playbackControls.setBackground(buttonBackground(0xf2192118));
        int margin = dp(24);
        playbackControls.setPadding(dp(8), dp(8), dp(8), dp(8));
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(-2, -2,
            Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        position.bottomMargin = margin;
        root.addView(playbackControls, position);
        if (isWatching() || fullscreen != null) {
            addPlaybackButton("Back 10s", () -> sendMediaAction("back", true));
            addPlaybackButton("Play / Pause", () -> sendMediaAction("toggle", true));
            addPlaybackButton("Forward 10s", () -> sendMediaAction("forward", true));
            addPlaybackButton("Servers", () -> openPlayerOption("Servers"));
            addPlaybackButton("Subtitles", () -> openPlayerOption("Subtitles"));
            addPlaybackButton("Settings", () -> openPlayerOption("Settings"));
        } else {
            addPlaybackButton("Home", () -> navigateTo(""));
            addPlaybackButton("Search", () -> navigateTo("search"));
            addPlaybackButton("Movies", () -> navigateTo("movies"));
            addPlaybackButton("Shows", () -> navigateTo("series"));
            addPlaybackButton("My List", () -> navigateTo("lists"));
        }
        addPlaybackButton("Pointer", () -> {
            hidePlaybackControls();
            setPointer(true);
        });
        menuSelection = 1;
        playbackControls.getChildAt(menuSelection).requestFocus();
    }

    private void navigateTo(String path) {
        hidePlaybackControls();
        if (!(HOME + path).equals(web.getUrl())) web.loadUrl(HOME + path);
    }

    private void openPlayerOption(String option) {
        hidePlaybackControls();
        if (fullscreen != null) hideFullscreen();
        remote("playerOption(" + JSONObject.quote(option) + ")", result -> {
            if (!"true".equals(result)) showMediaStatus("Could not open " + option.toLowerCase(java.util.Locale.ROOT));
        });
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable buttonBackground(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(10));
        return shape;
    }

    private void addPlaybackButton(String title, Runnable onClick) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setFocusableInTouchMode(true);
        button.setMinWidth(dp(54));
        button.setMinimumWidth(dp(54));
        button.setMinHeight(dp(48));
        button.setPadding(dp(10), 0, dp(10), 0);
        StateListDrawable selector = new StateListDrawable();
        selector.addState(new int[]{android.R.attr.state_focused}, buttonBackground(ACCENT));
        selector.addState(new int[0], buttonBackground(0xff263225));
        button.setBackground(selector);
        button.setTextColor(new android.content.res.ColorStateList(
            new int[][]{new int[]{android.R.attr.state_focused}, new int[0]},
            new int[]{BACKGROUND, Color.WHITE}));
        button.setOnClickListener(view -> onClick.run());
        LinearLayout.LayoutParams placement = new LinearLayout.LayoutParams(-2, -2);
        placement.setMargins(dp(4), 0, dp(4), 0);
        playbackControls.addView(button, placement);
    }

    private void hidePlaybackControls() {
        if (playbackControls == null) return;
        root.removeView(playbackControls);
        playbackControls = null;
        (fullscreen == null ? web : fullscreen).requestFocus();
    }

    private void remote(String call, ValueCallback<String> callback) {
        if (!isCineJoy(web.getUrl()) || error != null) {
            if (callback != null) callback.onReceiveValue("null");
            return;
        }
        web.evaluateJavascript("window.cineJoyTvRemote ? window.cineJoyTvRemote." + call + " : null", callback);
    }

    private static String direction(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_LEFT: return "left";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "right";
            case KeyEvent.KEYCODE_DPAD_UP: return "up";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "down";
            default: return null;
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (web == null || error != null) return super.dispatchKeyEvent(event);
        int key = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        boolean first = down && event.getRepeatCount() == 0;
        if (key == KeyEvent.KEYCODE_MENU) {
            if (first) {
                if (pointer) setPointer(false);
                else if (playbackControls != null) hidePlaybackControls();
                else showRemoteMenu();
            }
            return true;
        }
        String action = mediaAction(key);
        if (action != null && isCineJoy(web.getUrl())) {
            if (first) sendMediaAction(action, true);
            return true;
        }
        String arrow = direction(key);
        boolean select = key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_BUTTON_A ||
            key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (playbackControls != null && (arrow != null || select)) {
            if (down && ("left".equals(arrow) || "right".equals(arrow))) {
                int step = "left".equals(arrow) ? -1 : 1;
                menuSelection = Math.max(0, Math.min(playbackControls.getChildCount() - 1, menuSelection + step));
                playbackControls.getChildAt(menuSelection).requestFocus();
            }
            if (select && first) playbackControls.getChildAt(menuSelection).performClick();
            return true;
        }
        if (pointer && !keyboardVisible && (arrow != null || select)) {
            if (arrow != null && down) movePointer(arrow, event.getRepeatCount());
            if (select && first) tap(cursorX, cursorY, false);
            return true;
        }
        if (editing || keyboardVisible || fullscreen != null) return super.dispatchKeyEvent(event);
        if (arrow != null || select) {
            if (down && arrow != null &&
                    (event.getRepeatCount() == 0 || event.getRepeatCount() % 2 == 0)) {
                remote("move('" + arrow + "'," + event.getRepeatCount() + ")", null);
            }
            if (first && select) {
                final int version = pageVersion;
                remote("select()", point -> {
                    if (version == pageVersion) selectAt(point);
                });
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void selectAt(String result) {
        if (result == null || "null".equals(result)) return;
        try {
            JSONObject point = new JSONObject(result);
            if (point.optBoolean("playback")) {
                sendMediaAction("toggle", true);
                return;
            }
            View surface = fullscreen == null ? web : fullscreen;
            float x = (float) point.getDouble("x") * surface.getWidth();
            float y = (float) point.getDouble("y") * surface.getHeight();
            if (point.optBoolean("pointer")) {
                setPointer(true);
                cursorX = x;
                cursorY = y;
                cursor.invalidate();
                hoverPointer();
                return;
            }
            tap(x, y, point.optBoolean("keyboard"));
        } catch (JSONException exception) {
            android.util.Log.w("CineJoyTV", "Invalid remote coordinates", exception);
        }
    }

    private void tap(float x, float y, boolean keyboard) {
        View surface = fullscreen == null ? web : fullscreen;
        long now = SystemClock.uptimeMillis();
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, x, y, 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            surface.dispatchTouchEvent(event);
            event.recycle();
        }
        if (keyboard) {
            editing = true;
            InputMethodManager ime = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            web.postDelayed(() -> {
                if (editing && ime != null) ime.showSoftInput(web, InputMethodManager.SHOW_IMPLICIT);
            }, 160);
        }
    }

    private void setPointer(boolean enabled) {
        if (pointer == enabled) return;
        pointer = enabled;
        cursor.setVisibility(enabled ? View.VISIBLE : View.GONE);
        if (!enabled) return;
        editing = false;
        InputMethodManager ime = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (ime != null) ime.hideSoftInputFromWindow(web.getWindowToken(), 0);
        View surface = fullscreen == null ? web : fullscreen;
        cursorX = surface.getWidth() / 2f;
        cursorY = surface.getHeight() / 2f;
        remote("clear()", null);
        cursor.invalidate();
        Toast.makeText(this, "Pointer mode. Menu or Back to exit", Toast.LENGTH_SHORT).show();
    }

    private void movePointer(String direction, int repeats) {
        View surface = fullscreen == null ? web : fullscreen;
        float step = getResources().getDisplayMetrics().density * (repeats > 6 ? 30 : 14);
        float previousX = cursorX;
        float previousY = cursorY;
        if ("left".equals(direction)) cursorX -= step;
        if ("right".equals(direction)) cursorX += step;
        if ("up".equals(direction)) cursorY -= step;
        if ("down".equals(direction)) cursorY += step;
        cursorX = Math.max(1, Math.min(surface.getWidth() - 1, cursorX));
        cursorY = Math.max(1, Math.min(surface.getHeight() - 1, cursorY));
        cursor.invalidate();
        hoverPointer();
        if (previousX == cursorX && previousY == cursorY) {
            scrollPointer(surface, direction);
        }
    }

    private void scrollPointer(View surface, String direction) {
        MotionEvent.PointerProperties mouse = new MotionEvent.PointerProperties();
        mouse.id = 0;
        mouse.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords location = new MotionEvent.PointerCoords();
        location.x = cursorX;
        location.y = cursorY;
        if ("up".equals(direction)) location.setAxisValue(MotionEvent.AXIS_VSCROLL, 2);
        if ("down".equals(direction)) location.setAxisValue(MotionEvent.AXIS_VSCROLL, -2);
        if ("left".equals(direction)) location.setAxisValue(MotionEvent.AXIS_HSCROLL, -2);
        if ("right".equals(direction)) location.setAxisValue(MotionEvent.AXIS_HSCROLL, 2);
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1,
            new MotionEvent.PointerProperties[]{mouse}, new MotionEvent.PointerCoords[]{location},
            0, 0, 1, 1, 0, 0, InputDevice.SOURCE_MOUSE, 0);
        surface.dispatchGenericMotionEvent(event);
        event.recycle();
    }

    private void hoverPointer() {
        View surface = fullscreen == null ? web : fullscreen;
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_HOVER_MOVE, cursorX, cursorY, 0);
        event.setSource(InputDevice.SOURCE_MOUSE);
        surface.dispatchGenericMotionEvent(event);
        event.recycle();
    }

    private static final class Cursor extends View {
        private final Paint brush = new Paint(Paint.ANTI_ALIAS_FLAG);

        Cursor(Context context) {
            super(context);
        }

        @Override protected void onDraw(Canvas canvas) {
            MainActivity activity = (MainActivity) getContext();
            float radius = 7 * getResources().getDisplayMetrics().density;
            brush.setColor(BACKGROUND);
            canvas.drawCircle(activity.cursorX, activity.cursorY, radius, brush);
            brush.setColor(ACCENT);
            canvas.drawCircle(activity.cursorX, activity.cursorY, radius * 0.65f, brush);
        }
    }

    @Override public void onBackPressed() {
        if (playbackControls != null) {
            hidePlaybackControls();
            return;
        }
        if (pointer) {
            setPointer(false);
            return;
        }
        if (editing || keyboardVisible) {
            finishEditing();
            return;
        }
        if (fullscreen != null) {
            hideFullscreen();
            return;
        }
        if (leaving) return;
        leaving = true;
        remote("back()", handled -> {
            leaving = false;
            if ("true".equals(handled)) return;
            if (web.canGoBack()) web.goBack();
            else super.onBackPressed();
        });
    }

    private void finishEditing() {
        editing = false;
        InputMethodManager ime = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (ime != null) ime.hideSoftInputFromWindow(web.getWindowToken(), 0);
        remote("finishEditing()", null);
    }

    private void applyImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        web.saveState(state);
    }

    @Override protected void onPause() {
        sendMediaAction("pause", false);
        mediaSession.setActive(false);
        web.onPause();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        web.onResume();
        mediaSession.setActive(isWatching());
    }

    @Override protected void onDestroy() {
        mediaSession.release();
        handler.removeCallbacksAndMessages(null);
        root.removeView(web);
        web.destroy();
        super.onDestroy();
    }
}
