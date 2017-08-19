package net.yrom.screenrecorder.ui.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraAccessException;

import android.content.pm.PackageInfo;
import android.content.pm.ApplicationInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

import net.yrom.screenrecorder.R;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import android.media.MediaFormat;
import android.media.MediaCodec;
import java.io.IOException;
import android.text.TextUtils;

public class LaunchActivity extends AppCompatActivity {

//    @BindView(R.id.btn_screen_record)
//    Button btnScreenRecord;
//    @BindView(R.id.btn_camera_record)
//    Button btnCameraRecord;
    @BindView(R.id.lst_support_audio)
    ListView lstAudio;
    @BindView(R.id.lst_support_codec)
    ListView lstCodec;
    @BindView(R.id.web_1)
    WebView web_1;

    private ArrayAdapter<String> arrayAdapter;

    private static final int REQUEST_STREAM = 1;
    private static String[] PERMISSIONS_STREAM = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };
    boolean authorized = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch);
        ButterKnife.bind(this);

//        try {
//            getAvailableDecoders();
//        }catch(Exception ex){
//            ;
//        }

//        getPackages();

        web_1.getSettings().setDefaultTextEncodingName("UTF-8");
        web_1.setWebViewClient(mClient);
        web_1.loadData("<!DOCTYPE html><html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" /><title>title</title><body>录制选项</body></html>","text/html; charset=UTF-8",null);

//        String[] arrayData = {"苹果","香蕉","梨子","西瓜","桃子"};
        ArrayList<ArrayList<String>> arrData = SupportAvcCodec();
        ArrayList<String> arrEdata = arrData.get(0);
        if(arrEdata!=null) {
            arrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, (String[]) (arrEdata.toArray(new String[arrEdata.size()])));
            lstCodec.setAdapter(arrayAdapter);
        }

        lstAudio.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,new String[]{"外录","内录","全局"}));

        //得到摄像头的个数
//        CameraManager mManager = (CameraManager)getSystemService(CAMERA_SERVICE);
//        try {
//            mManager.getCameraIdList();
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }

        verifyPermissions();
    }

    private WebViewClient mClient = new WebViewClient(){
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return super.shouldOverrideUrlLoading(view, request);
        }
    };

    @OnClick({R.id.btn_screen_record, R.id.btn_camera_record})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.btn_screen_record:
                ScreenRecordActivity.launchActivity(this);
                break;
            case R.id.btn_camera_record:
                CameraActivity.launchActivity(this);
                break;
        }
    }

    public void verifyPermissions() {
        int CAMERA_permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        int RECORD_AUDIO_permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        int WRITE_EXTERNAL_STORAGE_permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int READ_EXTERNAL_STORAGE_permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        if (CAMERA_permission != PackageManager.PERMISSION_GRANTED ||
                RECORD_AUDIO_permission != PackageManager.PERMISSION_GRANTED ||
                WRITE_EXTERNAL_STORAGE_permission != PackageManager.PERMISSION_GRANTED ||
                READ_EXTERNAL_STORAGE_permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_STREAM,
                    REQUEST_STREAM
            );
            authorized = false;
        } else {
            authorized = true;
        }
    }

    private ArrayList<ArrayList<String>> SupportAvcCodec(){
        ArrayList<ArrayList<String>> ccc = new ArrayList<ArrayList<String>>();
        ArrayList<String> arrECodec = new ArrayList<String>();
        ArrayList<String> arrDCodec = new ArrayList<String>();
        MediaCodecList mRegularCodecs = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        MediaCodecInfo[] mRegularInfos = mRegularCodecs.getCodecInfos();
        if(Build.VERSION.SDK_INT>=18){
            for(int j = mRegularInfos.length - 1; j >= 0; j--){
                MediaCodecInfo codecInfo = mRegularInfos[j];
                String[] types = codecInfo.getSupportedTypes();
                for (int i = 0; i < types.length; i++)
                {
                    if(codecInfo.isEncoder())
                        arrECodec.add(types[i]);
                    else
                        arrDCodec.add(types[i]);
//                    if (types[i].equalsIgnoreCase("video/avc")) {
//                        return true;
//                    }
                }
            }
        }
//        return false;
        ccc.add(arrECodec);
        ccc.add(arrDCodec);
        return ccc;
    }

//    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static List<String> getAvailableEncoders() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            String encoderAsStr = mcl.findEncoderForFormat(MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720));
//            String encoderAsStr = mcl.findDecoderForFormat(MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720));
            List<String> encoders = new ArrayList<>();
            for (MediaCodecInfo info : mcl.getCodecInfos()) {
                if (info.isEncoder()) {
                    if (info.getName().equals(encoderAsStr)) {
                        encoders.add("*** " + info.getName() + ": " + TextUtils.join(", ", info.getSupportedTypes()));
                    } else {
                        encoders.add(info.getName() + ": " + TextUtils.join(", ", info.getSupportedTypes()));
                    }
                }
            }
            return encoders;
        }
        return Collections.emptyList();
    }

    public static List<String> getAvailableDecoders() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
//            String encoderAsStr = mcl.findEncoderForFormat(MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720));
            String encoderAsStr = mcl.findDecoderForFormat(MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720));
            List<String> encoders = new ArrayList<>();
            for (MediaCodecInfo info : mcl.getCodecInfos()) {
                if (!info.isEncoder()) {
                    if (info.getName().equals(encoderAsStr)) {
                        encoders.add("*** " + info.getName() + ": " + TextUtils.join(", ", info.getSupportedTypes()));
                    } else {
                        encoders.add(info.getName() + ": " + TextUtils.join(", ", info.getSupportedTypes()));
                    }
                }
            }
            return encoders;
        }
        return Collections.emptyList();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STREAM) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[1] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[2] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[3] == PackageManager.PERMISSION_GRANTED) {
                authorized = true;
            }
        }
    }


    private void getPackages() {
        // 获取已经安装的所有应用, PackageInfo　系统类，包含应用信息
        List<PackageInfo> packages = getPackageManager().getInstalledPackages(0);
        for (int i = 0; i < packages.size(); i++) {
            PackageInfo packageInfo = packages.get(i);
            if ((packageInfo.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM) == 0) { //非系统应用
//                // AppInfo 自定义类，包含应用信息
//                AppInfo appInfo = new AppInfo();
//                appInfo.setAppName(
//                        packageInfo.applicationInfo.loadLabel(getPackageManager()).toString());//获取应用名称
//                appInfo.setPackageName(packageInfo.packageName); //获取应用包名，可用于卸载和启动应用
//                appInfo.setVersionName(packageInfo.versionName);//获取应用版本名
//                appInfo.setVersionCode(packageInfo.versionCode);//获取应用版本号
//                appInfo.setAppIcon(packageInfo.applicationInfo.loadIcon(getPackageManager()));//获取应用图标
//                System.out.println(appInfo.toString());
//                mData.add(appInfo);
            } else { // 系统应用

            }
        }
    }
}

