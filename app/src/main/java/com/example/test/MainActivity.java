package com.example.test;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
//import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private final int PICK_REQUEST = 10001;
    private TextToSpeech textToSpeech;
    private boolean textToSpeechReady = false;
    ValueCallback<Uri> mFilePathCallback;
    ValueCallback<Uri[]> mFilePathCallbackArray;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //隐藏ActionBar
        Objects.requireNonNull(getSupportActionBar()).hide();
        setContentView(R.layout.activity_main);
        //WebView加载页面
        webView = findViewById(R.id.web_view);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        initNativeTextToSpeech();
        webView.addJavascriptInterface(new NativeTtsBridge(), "NativeTTS");
        // code from https://blog.csdn.net/qq_21138819/article/details/56676007 by 欢子-3824
        webView.setWebChromeClient(new WebChromeClient() {
            // Andorid 4.1----4.4
            public void openFileChooser(ValueCallback<Uri> uploadFile, String acceptType, String capture) {

                mFilePathCallback = uploadFile;
                handle(uploadFile);
            }

            // for 5.0+
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (mFilePathCallbackArray != null) {
                    mFilePathCallbackArray.onReceiveValue(null);
                }
                mFilePathCallbackArray = filePathCallback;
                handleup(filePathCallback);
                return true;
            }

            private void handle(ValueCallback<Uri> uploadFile) {
                Intent intent = new Intent(Intent.ACTION_PICK);
                // 设置允许上传的文件类型
                intent.setType("*/*");
                startActivityForResult(intent, PICK_REQUEST);
            }

            private void handleup(ValueCallback<Uri[]> uploadFile) {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("*/*");
                startActivityForResult(intent, PICK_REQUEST);
            }
        });

        // wevView监听 H5 页面的下载事件
        // code from https://github.com/madhan98/Android-webview-upload-download/blob/master/app/src/main/java/com/my/newproject/MainActivity.java by Madhan
        webView.setDownloadListener(new DownloadListener() {

            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {

                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));

                String cookies = CookieManager.getInstance().getCookie(url);

                request.addRequestHeader("cookie", cookies);

                request.addRequestHeader("User-Agent", userAgent);

                request.setDescription("下载中...");

                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype));

                request.allowScanningByMediaScanner(); request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED); request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimetype));

                DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);

                manager.enqueue(request);

                showMessage("下载中...");

                //Notif if success

                BroadcastReceiver onComplete = new BroadcastReceiver() {

                    public void onReceive(Context ctxt, Intent intent) {

                        showMessage("下载完成");

                        unregisterReceiver(this);

                    }};

                registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

            }

        });

        //该方法解决的问题是打开浏览器不调用系统浏览器，直接用 webView 打开
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectSpeechSynthesisPolyfill();
            }
        });

        // 这里填你需要打包的 H5 页面链接
        webView.loadUrl("https://chat.aifa.ink/");

        //显示一些小图片（头像）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        // 允许使用 localStorage sessionStorage
        webView.getSettings().setDomStorageEnabled(true);
        // 是否支持 html 的 meta 标签
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().getAllowUniversalAccessFromFileURLs();
        webView.getSettings().getAllowFileAccessFromFileURLs();
    }

    //设置回退页面
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK) && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Deprecated
    public void showMessage(String _s) {
        Toast.makeText(getApplicationContext(), _s, Toast.LENGTH_SHORT).show();
    }

    private void initNativeTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            textToSpeechReady = status == TextToSpeech.SUCCESS;
            if (textToSpeechReady) {
                int result = textToSpeech.setLanguage(Locale.CHINESE);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech.setLanguage(Locale.getDefault());
                }
                textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        notifySpeechEvent(utteranceId, "start", null);
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        notifySpeechEvent(utteranceId, "end", null);
                    }

                    @Override
                    public void onError(String utteranceId) {
                        notifySpeechEvent(utteranceId, "error", "native-tts-error");
                    }
                });
                notifyVoicesChanged();
            }
        });
    }

    private void notifySpeechEvent(String utteranceId, String eventName, String error) {
        if (webView == null || utteranceId == null) return;
        runOnUiThread(() -> webView.evaluateJavascript(
                "window.__nativeTtsDispatch && window.__nativeTtsDispatch(" +
                        escapeJsString(eventName) + "," +
                        escapeJsString(utteranceId) + "," +
                        escapeJsString(error) + ");",
                null
        ));
    }

    private void notifyVoicesChanged() {
        if (webView == null) return;
        runOnUiThread(() -> webView.evaluateJavascript(
                "window.__nativeTtsVoicesChanged && window.__nativeTtsVoicesChanged();",
                null
        ));
    }

    private String escapeJsString(String value) {
        if (value == null) return "null";
        return "'" + value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "'";
    }

    private void injectSpeechSynthesisPolyfill() {
        if (webView == null) return;

        String js = "(function(){" +
                "if(!window.NativeTTS||window.__nativeTtsPolyfillInstalled)return;" +
                "window.__nativeTtsPolyfillInstalled=true;" +
                "var utterances={};var listeners={voiceschanged:[]};var speaking=false;var pending=false;" +
                "function NativeSpeechSynthesisUtterance(text){this.text=text||'';this.lang='zh-CN';this.voice=null;this.volume=1;this.rate=1;this.pitch=1;this.onstart=null;this.onend=null;this.onerror=null;}" +
                "var voice={voiceURI:'native-android-tts',name:'Android 系统语音',lang:'zh-CN',localService:true,default:true};" +
                "function fire(type,id,error){var u=utterances[id];if(!u)return;var e={type:type,utterance:u,error:error||null};if(type==='start')speaking=true;if(type==='end'||type==='error'){speaking=false;pending=false;delete utterances[id];}var cb=u['on'+type];if(typeof cb==='function')cb.call(u,e);}" +
                "window.__nativeTtsDispatch=fire;" +
                "window.__nativeTtsVoicesChanged=function(){(listeners.voiceschanged||[]).slice().forEach(function(fn){try{fn.call(synth,{type:'voiceschanged'});}catch(e){}});};" +
                "var synth={" +
                "get speaking(){return speaking;},get pending(){return pending;},get paused(){return false;}," +
                "getVoices:function(){return [voice];}," +
                "speak:function(u){if(!u)return;var id='utt_'+Date.now()+'_'+Math.random().toString(16).slice(2);utterances[id]=u;pending=true;NativeTTS.speak(id,String(u.text||''),u.lang||(u.voice&&u.voice.lang)||'zh-CN',Number(u.rate)||1,Number(u.pitch)||1,Number(u.volume)||1);}," +
                "cancel:function(){pending=false;speaking=false;utterances={};NativeTTS.cancel();}," +
                "pause:function(){},resume:function(){NativeTTS.resume();}," +
                "addEventListener:function(type,fn){if(!listeners[type])listeners[type]=[];listeners[type].push(fn);}," +
                "removeEventListener:function(type,fn){var arr=listeners[type]||[];var i=arr.indexOf(fn);if(i>=0)arr.splice(i,1);}" +
                "};" +
                "window.SpeechSynthesisUtterance=NativeSpeechSynthesisUtterance;" +
                "window.speechSynthesis=synth;" +
                "setTimeout(function(){window.__nativeTtsVoicesChanged&&window.__nativeTtsVoicesChanged();},0);" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    private class NativeTtsBridge {
        @JavascriptInterface
        public void speak(String utteranceId, String text, String lang, float rate, float pitch, float volume) {
            if (!textToSpeechReady || textToSpeech == null) {
                notifySpeechEvent(utteranceId, "error", "native-tts-not-ready");
                return;
            }

            Locale locale = Locale.CHINESE;
            if (lang != null && lang.toLowerCase().startsWith("en")) {
                locale = Locale.ENGLISH;
            }
            int languageResult = textToSpeech.setLanguage(locale);
            if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                textToSpeech.setLanguage(Locale.getDefault());
            }

            textToSpeech.setSpeechRate(Math.max(0.1f, Math.min(rate, 3.0f)));
            textToSpeech.setPitch(Math.max(0.1f, Math.min(pitch, 3.0f)));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Bundle params = new Bundle();
                params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, Math.max(0.0f, Math.min(volume, 1.0f)));
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
            } else {
                HashMap<String, String> params = new HashMap<>();
                params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
                params.put(TextToSpeech.Engine.KEY_PARAM_VOLUME, String.valueOf(Math.max(0.0f, Math.min(volume, 1.0f))));
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params);
            }
        }

        @JavascriptInterface
        public void cancel() {
            if (textToSpeech != null) {
                textToSpeech.stop();
            }
        }

        @JavascriptInterface
        public void resume() {
            // Android TextToSpeech 没有与 Web Speech API 完全对应的 resume，这里保留为空实现兼容 H5 调用。
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        webView.destroy();
        webView = null;
    }


    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_REQUEST) {
            if (null != data) {
                Uri uri = data.getData();
                handleCallback(uri);
            } else {
                // 取消了照片选取的时候调用
                handleCallback(null);
            }
        } else {
            // 取消了照片选取的时候调用
            handleCallback(null);
        }
    }

    /**
     * 处理WebView的回调
     *
     * @param uri
     */
    private void handleCallback(Uri uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (mFilePathCallbackArray != null) {
                if (uri != null) {
                    mFilePathCallbackArray.onReceiveValue(new Uri[]{uri});
                } else {
                    mFilePathCallbackArray.onReceiveValue(null);
                }
                mFilePathCallbackArray = null;
            }
        } else {
            if (mFilePathCallback != null) {
                if (uri != null) {
                    String url = getFilePathFromContentUri(uri, getContentResolver());
                    Uri u = Uri.fromFile(new File(url));

                    mFilePathCallback.onReceiveValue(u);
                } else {
                    mFilePathCallback.onReceiveValue(null);
                }
                mFilePathCallback = null;
            }
        }
    }

    public static String getFilePathFromContentUri(Uri selectedVideoUri, ContentResolver contentResolver) {
        String filePath;
        String[] filePathColumn = {MediaStore.MediaColumns.DATA};

        Cursor cursor = contentResolver.query(selectedVideoUri, filePathColumn, null, null, null);
//      也可用下面的方法拿到cursor
//      Cursor cursor = this.context.managedQuery(selectedVideoUri, filePathColumn, null, null, null);

        cursor.moveToFirst();

        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
        filePath = cursor.getString(columnIndex);
        cursor.close();
        return filePath;
    }

}
