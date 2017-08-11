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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import java.util.ArrayList;

import net.yrom.screenrecorder.R;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class LaunchActivity extends AppCompatActivity {

//    @BindView(R.id.btn_screen_record)
//    Button btnScreenRecord;
//    @BindView(R.id.btn_camera_record)
//    Button btnCameraRecord;
    @BindView(R.id.lst_support_audio)
    ListView lstAudio;
    @BindView(R.id.lst_support_codec)
    ListView lstCodec;
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

//        String[] arrayData = {"苹果","香蕉","梨子","西瓜","桃子"};
        ArrayList<String> arrData = SupportAvcCodec();
        arrayAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,(String[])(arrData.toArray(new String[arrData.size()])));
        lstCodec.setAdapter(arrayAdapter);

        lstAudio.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,new String[]{"外录","内录","全局"}));

        verifyPermissions();
    }

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

    private ArrayList<String> SupportAvcCodec(){
        ArrayList<String> arrCodec = new ArrayList<String>();
        if(Build.VERSION.SDK_INT>=18){
            for(int j = MediaCodecList.getCodecCount() - 1; j >= 0; j--){
                MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(j);
                String[] types = codecInfo.getSupportedTypes();
                for (int i = 0; i < types.length; i++) {
                    arrCodec.add(types[i]);
//                    if (types[i].equalsIgnoreCase("video/avc")) {
//                        return true;
//                    }
                }
            }
        }
//        return false;
        return arrCodec;
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
}
