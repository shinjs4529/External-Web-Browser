package com.kist.externalwebbrowser;

import static org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE;
import static org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_MOBILE;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;


public class GeckoViewerActivity extends AppCompatActivity {
    public GeckoView view = null;
    static Handler handler = new Handler();

    private class contentBlockingDelegate
            implements ContentBlocking.Delegate {

        @Override
        public void onContentBlocked(final GeckoSession session,
                                     final ContentBlocking.BlockEvent event) {
        }
    }
    private class PageProgressDelegate implements GeckoSession.ProgressDelegate{

        protected boolean m_bBlankPage = true;

        private PageProgressDelegate(final contentBlockingDelegate cb){

        }
        @Override
        public void onPageStart(@NonNull GeckoSession var1, @NonNull String var2) {
            m_bBlankPage = var2.equals("about:blank");
            Log.i("onPageStart", var1 + ", " + var2);
        }

        @Override
        public void onPageStop(GeckoSession session, boolean success){
            if(!m_bBlankPage) {
                Log.i("PPD", "Stopping page load " + (success ? "successfully" : "unsuccessfully"));
                Log.e("PPD", "EndOfCreate");
            }
            else {
                Log.e("PPD", "BlackPage");
            }
        }
    }

    private class ExamplePermissionDelegate implements GeckoSession.PermissionDelegate {
        public int androidPermissionRequestCode = 1;
        private String TAG = "GeckoViewer.ExamplePermissionDelegate";

        private LinearLayout addStandardLayout(final AlertDialog.Builder builder,
                                               final String title, final String msg) {
            final ScrollView scrollView = new ScrollView(builder.getContext());
            final LinearLayout container = new LinearLayout(builder.getContext());
            final int horizontalPadding = getViewPadding(builder);
            final int verticalPadding = (msg == null || msg.isEmpty()) ? horizontalPadding : 0;
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(/* left */ horizontalPadding, /* top */ verticalPadding,
                    /* right */ horizontalPadding, /* bottom */ verticalPadding);
            scrollView.addView(container);
            builder.setTitle(title)
                    .setMessage(msg)
                    .setView(scrollView);
            return container;
        }
        private int getViewPadding(final AlertDialog.Builder builder) {
            final TypedArray attr = builder.getContext().obtainStyledAttributes(
                    new int[]{android.R.attr.listPreferredItemPaddingLeft});
            final int padding = attr.getDimensionPixelSize(0, 1);
            attr.recycle();
            return padding;
        }

        private Spinner addMediaSpinner(final Context context, final ViewGroup container,
                                        final MediaSource[] sources, final String[] sourceNames) {
            final ArrayAdapter<MediaSource> adapter = new ArrayAdapter<MediaSource>(
                    context, android.R.layout.simple_spinner_item) {
                private View convertView(final int position, final View view) {
                    if (view != null) {
                        final MediaSource item = getItem(position);
                        ((TextView) view).setText(sourceNames != null ? sourceNames[position] : item.name);
                    }
                    return view;
                }


                @Override
                public View getView(final int position, View view,
                                    final ViewGroup parent) {
                    return convertView(position, super.getView(position, view, parent));
                }

                @Override
                public View getDropDownView(final int position, final View view,
                                            final ViewGroup parent) {
                    return convertView(position, super.getDropDownView(position, view, parent));
                }
            };
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            adapter.addAll(sources);

            final Spinner spinner = new Spinner(context);
            spinner.setAdapter(adapter);
            spinner.setSelection(0);
            container.addView(spinner);
            return spinner;
        }

        private String[] normalizeMediaName(final MediaSource[] sources) {
            if (sources == null) {
                return null;
            }

            String[] res = new String[sources.length];
            for (int i = 0; i < sources.length; i++) {
                final int mediaSource = sources[i].source;
                final String name = sources[i].name;
                res[i] = "장치 이름";
            }
            return res;
        }

        @Override
        public void onAndroidPermissionsRequest(GeckoSession session, String[] permissions, Callback callback)
        {
            if (ContextCompat.checkSelfPermission(GeckoViewerActivity.this,
                    Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED){
                Log.i(TAG, "Android Permission Needed");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(permissions, androidPermissionRequestCode);
                }
                callback = new ExamplePermissionCallback();
                callback.grant();
            }
            else{
                Log.i(TAG, "Android Permission Granted");
                callback.grant();
            }
        }

        @Override
        public void onContentPermissionRequest(final GeckoSession session,
                                               final String uri,
                                               final int type, final Callback callback) {
            Log.i(TAG, "Content Permission Needed");
        }
        @Override
        public void onMediaPermissionRequest (GeckoSession session, String uri, MediaSource[] video, MediaSource[] audio, MediaCallback callback)
        {
            Log.d(TAG, "onMediaPermissionRequest 진입");
            // Reject permission if Android perission has benn previously denied.
            if ((audio != null
                    && ContextCompat.checkSelfPermission(GeckoViewerActivity.this,
                    Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                    || (video != null
                    && ContextCompat.checkSelfPermission(GeckoViewerActivity.this,
                    Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
                callback.reject();
                return;
            }

            final String host = Uri.parse(uri).getAuthority();
            final String title = "미디어 요청";

            // Get the media device name from the `MediaDevice`
            String[] videoNames = normalizeMediaName(video);
            String[] audioNames = normalizeMediaName(audio);

            final AlertDialog.Builder builder = new AlertDialog.Builder(GeckoViewerActivity.this);

            // Create drop down boxes to allow users to select which device to grant permission to
            final LinearLayout container = addStandardLayout(builder, title, null);
            final Spinner videoSpinner;
            if (video != null) {
                videoSpinner = addMediaSpinner(builder.getContext(), container, video, videoNames); // create spinner and add to alert UI
            } else {
                videoSpinner = null;
            }

            final Spinner audioSpinner;
            if (audio != null) {
                audioSpinner = addMediaSpinner(builder.getContext(), container, audio, audioNames); // create spinner and add to alert UI
            } else {
                audioSpinner = null;
            }

            builder.setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(final DialogInterface dialog, final int which) {
                                    // gather selected media devices and grant access
                                    final MediaSource video = (videoSpinner != null)
                                            ? (MediaSource) videoSpinner.getSelectedItem() : null;
                                    final MediaSource audio = (audioSpinner != null)
                                            ? (MediaSource) audioSpinner.getSelectedItem() : null;
                                    callback.grant(video, audio);
//                                    Log.d(TAG, "마이크, 카메라 승인"+video);
                                }
                            });

            // 다이얼로그 없이 권한 바로 부여 (Android 자체 권한만 있으면 바로 부여 가능!)
            final MediaSource videoSource = (videoSpinner != null) ? (MediaSource) videoSpinner.getSelectedItem() : null;
            final MediaSource audioSource = (audioSpinner != null) ? (MediaSource) audioSpinner.getSelectedItem() : null;
            callback.grant(videoSource, audioSource);
        }
    }
    public class ExamplePermissionCallback implements GeckoSession.PermissionDelegate.Callback {
        private String TAG = "GeckoViewer.ExamplePermissionCallback";
        @Override
        public void grant() {
            Log.d(TAG, "ExamplePermissionCallback grant 진입");
            int permission = ContextCompat.checkSelfPermission(GeckoViewerActivity.this,
                    Manifest.permission.CAMERA);
            if (permission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(GeckoViewerActivity.this,
                        new String[]{Manifest.permission.CAMERA},
                        1);}
        }
        @Override
        public void reject() {
        }
    }

    public interface EventListener {
        void EndOfCreate(GeckoViewerActivity actViewer, boolean bRes);
        void BeginOfDestroy(GeckoViewerActivity actViewer);
    }
    protected static EventListener m_eventListener = null;
    public static void SetEventListener(EventListener eventListener) { m_eventListener = eventListener; }

    GeckoSession m_session;
    GeckoRuntime m_runtime;

    //   protected static GeckoViewerActivity m_activityEx = null;
    public void StopActivity() {
        try {
            Log.e("Gecko.StopActivity", this.toString());
            this.finish();
        }
        catch (NullPointerException e) {
            // possible case
            Log.e("Gecko.StopActivity", "NullPointerException");
        }
//        try {
//            Log.e("GeckoViewerActivity.StopActivity", m_activityEx.toString());
//            m_activityEx.finish();
//        }
//        catch (NullPointerException e) {
//            // possible case
//            Log.e("GeckoViewerActivity.StopActivity", "NullPointerException");
//        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
//        if(m_activityEx != null) {
//            m_activityEx.finish();
//            Log.e("GeckoViewerActivity.onCreate", "m_activityEx != null");
//            try {
//                while(m_activityEx != null) {
//                    Thread.sleep(500);
//                    Log.e("GeckoViewerActivity.onCreate", "sleep 500ms");
//                }
//            }
//            catch(InterruptedException e) {
//                Log.e("GeckoViewerActivity.onCreate", e.toString());
//            }
//        }

        MyBroadcastReceiver mReceiver = new MyBroadcastReceiver();
        registerReceiver(mReceiver,
                new IntentFilter("first_app_packagename"));

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gecko_viewer);

        view = findViewById(R.id.geckoview);

        String strURL = getIntent().getStringExtra("url");
        if(strURL==null || strURL.isEmpty()){
            strURL = "https://www.google.com";
        }

        GeckoSessionSettings.Builder settingsBuilder = new GeckoSessionSettings.Builder();
        settingsBuilder
                .usePrivateMode(true)
                .useTrackingProtection(true)
                .userAgentMode(USER_AGENT_MODE_MOBILE)
                .userAgentOverride("")
                .suspendMediaWhenInactive(false)
                .fullAccessibilityTree(true)
                .viewportMode(VIEWPORT_MODE_MOBILE)
//                .useMultiprocess(true)
                .allowJavascript(true);

        GeckoRuntimeSettings.Builder runtimeSettingsBuilder = new GeckoRuntimeSettings.Builder();

        runtimeSettingsBuilder
                .consoleOutput(true)
                .javaScriptEnabled(true)
                .remoteDebuggingEnabled(true)
//                .displayDpiOverride(720)//int
//                .screenSizeOverride(720, 1080)//int
//                .useMaxScreenDepth(true)
//                .inputAutoZoomEnabled(true)
//                .forceUserScalableEnabled(true)
//                .displayDensityOverride(1.5f)
                .aboutConfigEnabled(true);
//        if(strURL.contains("www.bbsi.co.kr")) {//불교 방송인 경우
//            System.err.println("strURL contains bbsi");
//            runtimeSettingsBuilder.displayDensityOverride(2.0f);
//        }

        m_session = new GeckoSession(settingsBuilder.build());
        final contentBlockingDelegate cb = new contentBlockingDelegate();
        m_session.setContentBlockingDelegate(cb);
        m_session.setProgressDelegate(new PageProgressDelegate(cb));

        m_runtime = GeckoRuntime.getDefault(this);

        final ExamplePermissionDelegate permission = new ExamplePermissionDelegate();
        m_session.setPermissionDelegate(permission);

        m_session.open(m_runtime);

        view.setSession(m_session);

        Log.e("Viewer.URL", strURL);
        m_session.loadUri(strURL);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if(m_eventListener != null)
            m_eventListener.EndOfCreate(this, true);

//       m_activityEx = this;



        Log.i("Gecko.onCreate", this.toString());
    }

    @Override
    protected void onDestroy() {
        if(m_eventListener != null)
            m_eventListener.BeginOfDestroy(this);

        handler.removeCallbacksAndMessages(null);
        m_session.setMediaDelegate(null);
//         m_session.setMediaSessionDelegate(null);
        m_session.setPermissionDelegate(null);
        m_session.close();
        m_session = null;
        m_runtime = null;

        Log.i("Gecko.onDestroy", this.toString());
        super.onDestroy();
    }
}

