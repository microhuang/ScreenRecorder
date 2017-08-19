/*
 * Copyright (c) 2014 Yrom Wang <http://www.yrom.net>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.yrom.screenrecorder.ui.activity;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import net.yrom.screenrecorder.IScreenRecorderAidlInterface;
import net.yrom.screenrecorder.R;
import net.yrom.screenrecorder.core.RESAudioClient;
import net.yrom.screenrecorder.core.RESCoreParameters;
import net.yrom.screenrecorder.model.DanmakuBean;
import net.yrom.screenrecorder.rtmp.RESFlvData;
import net.yrom.screenrecorder.rtmp.RESFlvDataCollecter;
import net.yrom.screenrecorder.service.ScreenRecordListenerService;
import net.yrom.screenrecorder.task.RtmpStreamingSender;
import net.yrom.screenrecorder.task.ScreenRecorder;
import net.yrom.screenrecorder.tools.LogTools;

import java.util.ArrayList;
import android.widget.ArrayAdapter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.io.File;
import android.os.Environment;
import android.os.Build;
import android.media.MediaCodecList;
import android.media.MediaCodecInfo;

public class ScreenRecordActivity extends Activity implements View.OnClickListener {
    private static final int REQUEST_CODE = 1;
    private Button mButton;
    private EditText mRtmpAddET;
    //1
    private MediaProjectionManager mMediaProjectionManager;
    private ScreenRecorder mVideoRecorder;
    private RESAudioClient audioClient;
    private RtmpStreamingSender streamingSender;
    private ExecutorService executorService;
    private List<DanmakuBean> danmakuBeanList = new ArrayList<>();
    private String rtmpAddr;
    private boolean isRecording;
    private RESCoreParameters coreParameters;

    private ScreenRecordListenerService screenRecordListenerService;

    public static void launchActivity(Context ctx) {
        Intent it = new Intent(ctx, ScreenRecordActivity.class);
        it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(it);
    }

    private IScreenRecorderAidlInterface recorderAidlInterface;
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
//            recorderAidlInterface = IScreenRecorderAidlInterface.Stub.asInterface(service);
            recorderAidlInterface = IScreenRecorderAidlInterface.Stub.asInterface(
                ((ScreenRecordListenerService.TrackerBinder)service).getBinder()
            );

            screenRecordListenerService = ((ScreenRecordListenerService.TrackerBinder)service).getService();

            //调用后台服务方法
//            screenRecordListenerService.getMsg();

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            recorderAidlInterface = null;
            screenRecordListenerService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mButton = (Button) findViewById(R.id.button);
        mRtmpAddET = (EditText) findViewById(R.id.et_rtmp_address);
        mButton.setOnClickListener(this);
        //2
        mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //4
        MediaProjection mediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
        if (mediaProjection == null) {
            Log.e("@@", "media projection is null");
            return;
        }
        rtmpAddr = mRtmpAddET.getText().toString().trim();
        if (TextUtils.isEmpty(rtmpAddr)) {
            Toast.makeText(this, "rtmp address cannot be null", Toast.LENGTH_SHORT).show();
            return;
        }
        streamingSender = new RtmpStreamingSender();
        streamingSender.sendStart(rtmpAddr);
        RESFlvDataCollecter collecter = new RESFlvDataCollecter() {
            @Override
            public void collect(RESFlvData flvData, int type) {
                if(streamingSender!=null)
                {
                    streamingSender.sendFood(flvData, type); //停止时空指针、段错误？
                }
            }
        };
        coreParameters = new RESCoreParameters();

        audioClient = new RESAudioClient(coreParameters);

        if (!audioClient.prepare()) {
            LogTools.d("!!!!!audioClient.prepare()failed");
            return;
        }

        File file = new File(Environment.getExternalStorageDirectory(),
                "record-" + RESFlvData.VIDEO_WIDTH + "x" + RESFlvData.VIDEO_HEIGHT + "-" + System.currentTimeMillis() + ".mp4");
        //5
        //rtmp发送
        mVideoRecorder = new ScreenRecorder(collecter, RESFlvData.VIDEO_WIDTH, RESFlvData.VIDEO_HEIGHT, RESFlvData.VIDEO_BITRATE, 1, mediaProjection);
        //写文件
//        mVideoRecorder = new ScreenRecorder(file.getAbsolutePath(), RESFlvData.VIDEO_WIDTH, RESFlvData.VIDEO_HEIGHT, RESFlvData.VIDEO_BITRATE, 1, mediaProjection);
        mVideoRecorder.start();

        audioClient.start(collecter);

        executorService = Executors.newCachedThreadPool();
        executorService.execute(streamingSender);

        mButton.setText("Stop Recorder");
        Toast.makeText(this, "Screen recorder is running...", Toast.LENGTH_SHORT).show();
        moveTaskToBack(true);
    }

    @Override
    public void onClick(View v) {
        if (mVideoRecorder != null) {
            stopScreenRecord();
        } else {
            createScreenCapture();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mVideoRecorder != null) {
            stopScreenRecord();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isRecording) stopScreenRecordService();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isRecording) startScreenRecordService();
    }

    private void startScreenRecordService() {
        if (mVideoRecorder != null && mVideoRecorder.getStatus()) {
            //启动服务
            Intent runningServiceIT = new Intent(this, ScreenRecordListenerService.class);
            bindService(runningServiceIT, connection, BIND_AUTO_CREATE);
            startService(runningServiceIT);
            //与后台服务交互
//            screenRecordListenerService.getMsg();

            startAutoSendDanmaku();
        }
    }

    private void startAutoSendDanmaku() {
        ExecutorService exec = Executors.newCachedThreadPool();
        exec.execute(new Runnable() {
            @Override
            public void run() {
                int index = 0;
                while (true) {
                    DanmakuBean danmakuBean = new DanmakuBean();
                    danmakuBean.setMessage(String.valueOf(index++));
                    danmakuBean.setName("little girl");
                    danmakuBeanList.add(danmakuBean);
                    try {
                        if (recorderAidlInterface != null) {
                            recorderAidlInterface.sendDanmaku(danmakuBeanList);
                        }
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void stopScreenRecordService() {
        //停止服务
        Intent runningServiceIT = new Intent(this, ScreenRecordListenerService.class);
//        unbindService(connection);
        stopService(runningServiceIT);

        if (mVideoRecorder != null && mVideoRecorder.getStatus()) {
            Toast.makeText(this, "现在正在进行录屏直播哦", Toast.LENGTH_SHORT).show();
        }
    }

    private void createScreenCapture() {
        isRecording = true;
        //3
        Intent captureIntent = mMediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(captureIntent, REQUEST_CODE);
    }

    private void stopScreenRecord() {
        mVideoRecorder.quit();
        mVideoRecorder = null;
        if (streamingSender != null) {
            streamingSender.sendStop();
            streamingSender.quit(); //停止时空指针、段错误？
            streamingSender = null;
        }
        if (executorService != null) {
            executorService.shutdown();
            executorService = null;
        }
        mButton.setText("Restart recorder");
    }

    public static class RESAudioBuff {
        public boolean isReadyToFill;
        public int audioFormat = -1;
        public byte[] buff;

        public RESAudioBuff(int audioFormat, int size) {
            isReadyToFill = true;
            this.audioFormat = audioFormat;
            buff = new byte[size];
        }
    }

}
