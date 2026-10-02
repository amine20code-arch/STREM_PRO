package dz.stream.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;
import java.io.InputStreamReader;
import android.util.JsonReader;
import android.util.JsonToken;
import android.webkit.RenderProcessGoneDetail;

public class MainActivity extends Activity {
    private static final int REQ_FILE = 1;
    private static final int REQ_PLAY = 2;
    private int pendingZap = 0;
    private WebView web;
    private View customView;
    private WebChromeClient chrome;
    private ValueCallback<Uri[]> fileCb;
    private final ConcurrentHashMap<String, String> big = new ConcurrentHashMap<String, String>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        recreate();
                    }
                });
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Android");
        chrome = new WebChromeClient() {
            @Override
            public void onShowCustomView(View v, CustomViewCallback cb) {
                customView = v;
                setContentView(v);
            }

            @Override
            public void onHideCustomView() {
                setContentView(web);
                customView = null;
            }

            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(Intent.createChooser(i, "M3U / TXT"), REQ_FILE);
                } catch (Exception e) {
                    fileCb = null;
                    cb.onReceiveValue(null);
                    return true;
                }
                return true;
            }
        };
        web.setWebChromeClient(chrome);
        web.loadUrl("file:///android_asset/index.html");
        web.requestFocus();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PLAY) {
            if (res == RESULT_OK && data != null) pendingZap = data.getIntExtra("zap", 0);
            return;
        }
        if (req == REQ_FILE && fileCb != null) {
            Uri[] r = null;
            if (res == RESULT_OK && data != null && data.getData() != null) {
                r = new Uri[]{data.getData()};
            }
            fileCb.onReceiveValue(r);
            fileCb = null;
        }
    }

    // طلبات HTTP مع ترويسات مخصصة (Cookie ...) لا يسمح بها المتصفح
    // يستخرج الاسم والأمر فقط من قائمة قنوات Stalker الضخمة دون تحميلها كلها في الذاكرة
    private String compact(InputStream is) throws IOException {
        JsonReader r = new JsonReader(new InputStreamReader(is, "UTF-8"));
        r.setLenient(true);
        StringBuilder sb = new StringBuilder("{\"js\":{\"data\":[");
        boolean first = true;
        r.beginObject();
        while (r.hasNext()) {
            String k = r.nextName();
            if (k.equals("js")) {
                JsonToken tk = r.peek();
                if (tk == JsonToken.BEGIN_OBJECT) {
                    r.beginObject();
                    while (r.hasNext()) {
                        String k2 = r.nextName();
                        if (k2.equals("data") && r.peek() == JsonToken.BEGIN_ARRAY) first = readChannels(r, sb, first);
                        else r.skipValue();
                    }
                    r.endObject();
                } else if (tk == JsonToken.BEGIN_ARRAY) {
                    first = readChannels(r, sb, first);
                } else r.skipValue();
            } else r.skipValue();
        }
        r.endObject();
        sb.append("]}}");
        return sb.toString();
    }

    private boolean readChannels(JsonReader r, StringBuilder sb, boolean first) throws IOException {
        r.beginArray();
        while (r.hasNext()) {
            if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); continue; }
            String name = "", cmd = "";
            r.beginObject();
            while (r.hasNext()) {
                String f = r.nextName();
                if ((f.equals("name") || f.equals("cmd")) && r.peek() == JsonToken.STRING) {
                    String v = r.nextString();
                    if (f.equals("name")) name = v; else cmd = v;
                } else r.skipValue();
            }
            r.endObject();
            if (cmd.length() == 0) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"name\":").append(JSONObject.quote(name)).append(",\"cmd\":").append(JSONObject.quote(cmd)).append('}');
        }
        r.endArray();
        return first;
    }

    private class Bridge {
        @JavascriptInterface
        public String part(String id, int off, int n) {
            String s = big.get(id);
            if (s == null || off >= s.length()) return "";
            return s.substring(off, Math.min(s.length(), off + n));
        }

        @JavascriptInterface
        public void free(String id) {
            big.remove(id);
        }

        @JavascriptInterface
        public void playNative(final String url, final String title) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent i = new Intent(MainActivity.this, PlayerActivity.class);
                    i.putExtra("url", url);
                    i.putExtra("title", title);
                    startActivityForResult(i, REQ_PLAY);
                }
            });
        }

        @JavascriptInterface
        public void http(final String id, final String url, final String headersJson) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    int status = -1;
                    String body = "";
                    HttpURLConnection c = null;
                    try {
                        c = (HttpURLConnection) new URL(url).openConnection();
                        c.setConnectTimeout(15000);
                        c.setReadTimeout(30000);
                        c.setRequestProperty("Accept", "*/*");
                        JSONObject h = new JSONObject(headersJson);
                        Iterator<String> it = h.keys();
                        while (it.hasNext()) {
                            String k = it.next();
                            c.setRequestProperty(k, h.getString(k));
                        }
                        status = c.getResponseCode();
                        InputStream is = status >= 400 ? c.getErrorStream() : c.getInputStream();
                        if (is != null && status < 400 && url.contains("action=get_all_channels")) {
                            body = compact(is);
                            is.close();
                        } else if (is != null) {
                            ByteArrayOutputStream bo = new ByteArrayOutputStream();
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = is.read(buf)) != -1) bo.write(buf, 0, n);
                            body = bo.toString("UTF-8");
                            is.close();
                        }
                    } catch (Throwable e) {
                        status = -1;
                        body = String.valueOf(e);
                    } finally {
                        if (c != null) c.disconnect();
                    }
                    final String js;
                    if (body.length() > 200000) {
                        big.put(id, body);
                        js = "window.__natBig(" + JSONObject.quote(id) + "," + status + "," + body.length() + ")";
                    } else {
                        js = "window.__nat(" + JSONObject.quote(id) + "," + status + ","
                                + JSONObject.quote(body) + ")";
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            web.evaluateJavascript(js, null);
                        }
                    });
                }
            }).start();
        }
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK) {
            if (customView != null) {
                chrome.onHideCustomView();
                return true;
            }
            web.evaluateJavascript("(window.__back&&window.__back())?'1':'0'", new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String r) {
                    if (!"\"1\"".equals(r)) finish();
                }
            });
            return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        if (pendingZap != 0) {
            int z = pendingZap;
            pendingZap = 0;
            web.evaluateJavascript("window.__zap(" + z + ")", null);
        }
    }
}
