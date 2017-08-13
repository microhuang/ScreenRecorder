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

package net.yrom.screenrecorder.task;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.util.Log;
import android.view.Surface;

import net.yrom.screenrecorder.core.Packager;
import net.yrom.screenrecorder.rtmp.RESFlvData;
import net.yrom.screenrecorder.rtmp.RESFlvDataCollecter;
import net.yrom.screenrecorder.tools.LogTools;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.yrom.screenrecorder.rtmp.RESFlvData.FLV_RTMP_PACKET_TYPE_VIDEO;

/**
 * @author Yrom
 * Modified by raomengyang 2017-03-12
 */
public class ScreenRecorder extends Thread {
    private static final String TAG = "ScreenRecorder";

    private int mWidth;
    private int mHeight;
    private int mBitRate;
    private int mDpi;
    private String mDstPath;
    private MediaProjection mMediaProjection;
    // parameters for the encoder
    private static final String MIME_TYPE = "video/hevc";//"video/avc"  H.264 Advanced Video Coding        //video/hevc   H.265
    private static final int FRAME_RATE = 30; // 30 fps
    private static final int IFRAME_INTERVAL = 1;//2; // 2 seconds between I-frames
    private static final int TIMEOUT_US = 10000;

    private MediaCodec mEncoder;
    private Surface mSurface;
    private MediaMuxer mMuxer;
    private long startTime = 0;
    private AtomicBoolean mQuit = new AtomicBoolean(false);
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private VirtualDisplay mVirtualDisplay;
    private RESFlvDataCollecter mDataCollecter;

    private boolean mMuxerStarted = false;
    private int mVideoTrackIndex = -1;

    //rtmp发送
    public ScreenRecorder(RESFlvDataCollecter dataCollecter, int width, int height, int bitrate, int dpi, MediaProjection mp) {
        super(TAG);
        mWidth = width;
        mHeight = height;
        mBitRate = bitrate;
        mDpi = dpi;
        mMediaProjection = mp;
        startTime = 0;
        mDataCollecter = dataCollecter;
    }

    //写文件
    public ScreenRecorder(String dstPath, int width, int height, int bitrate, int dpi, MediaProjection mp) {
        super(TAG);
        mWidth = width;
        mHeight = height;
        mBitRate = bitrate;
        mDpi = dpi;
        mMediaProjection = mp;
        startTime = 0;
        mDstPath = dstPath;
    }

    /**
     * stop task
     */
    public final void quit() {
        mQuit.set(true);
    }

    @Override
    public void run() {
        try {
            try {
                prepareEncoder();
                if(!mDstPath.isEmpty()) {
                    mMuxer = new MediaMuxer(mDstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);//混合器
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            mVirtualDisplay = mMediaProjection.createVirtualDisplay(TAG + "-display",
                    mWidth, mHeight, mDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    mSurface, null, null);
            Log.d(TAG, "created virtual display: " + mVirtualDisplay);
            recordVirtualDisplay();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            release();
        }
    }


    private void prepareEncoder() throws IOException {
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);//MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        format.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        Log.d(TAG, "created video format: " + format);
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);//指定编码类型，模拟器h265上失败
//        mEncoder = MediaCodec.createByCodecName("OMX.google.h264.encoder"); //OMX.qcom.video.encoder.avc   OMX.google.hevc.decoder
//        mEncoder = MediaCodec.createByCodecName("OMX.google.hevc.encoder");
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mSurface = mEncoder.createInputSurface();
        Log.d(TAG, "created input surface: " + mSurface);
        mEncoder.start();
    }

    private void recordVirtualDisplay() {
        while (!mQuit.get()) {
            int eobIndex = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_US);
            switch (eobIndex) {
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    // no output available yet
                    Log.d(TAG, "no output from encoder available");
//                    LogTools.d("VideoSenderThread,MediaCodec.INFO_TRY_AGAIN_LATER");
                    try {
                        // wait 10ms
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        ;
                    }
                    break;
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    LogTools.d("VideoSenderThread,MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED");
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    LogTools.d("VideoSenderThread,MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:" +
                            mEncoder.getOutputFormat().toString());
                    if(!mDstPath.isEmpty()){
                        resetOutputFormat();
                    }else {
                        sendAVCDecoderConfigurationRecord(0, mEncoder.getOutputFormat());
                    }
                    break;
                default:
                    LogTools.d("VideoSenderThread,MediaCode,eobIndex=" + eobIndex);
                    if(!mDstPath.isEmpty()){
                        if (!mMuxerStarted) {
                            throw new IllegalStateException("MediaMuxer dose not call addTrack(format) ");
                        }
                        encodeToVideoTrack(eobIndex);
                    }else {
                        if (startTime == 0) {
                            startTime = mBufferInfo.presentationTimeUs / 1000;
                        }
                        /**
                         * we send sps pps already in INFO_OUTPUT_FORMAT_CHANGED
                         * so we ignore MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                         */
                        //
                        if (mBufferInfo.flags != MediaCodec.BUFFER_FLAG_CODEC_CONFIG && mBufferInfo.size != 0) {
                            ByteBuffer realData = mEncoder.getOutputBuffers()[eobIndex];
                            realData.position(mBufferInfo.offset + 4);
                            realData.limit(mBufferInfo.offset + mBufferInfo.size);
                            sendRealData((mBufferInfo.presentationTimeUs / 1000) - startTime, realData);
                        }
                    }
                    mEncoder.releaseOutputBuffer(eobIndex, false);
//                    mEncoder.releaseOutputBuffer(eobIndex, true);
//                    mEncoder.releaseOutputBuffer(eobIndex, timestamp);
                    break;
            }
        }
    }


    private void release() {
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
        if (mMediaProjection != null) {
            mMediaProjection.stop();
        }
        if (mMuxer != null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }
    }


    public final boolean getStatus() {
        return !mQuit.get();
    }

    private void encodeToVideoTrack(int index) {
        ByteBuffer encodedData = mEncoder.getOutputBuffer(index);

        if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            // The codec config data was pulled out and fed to the muxer when we got
            // the INFO_OUTPUT_FORMAT_CHANGED status.
            // Ignore it.
            Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
            mBufferInfo.size = 0;
        }
        if (mBufferInfo.size == 0) {
            Log.d(TAG, "info.size == 0, drop it.");
            encodedData = null;
        } else {
            Log.d(TAG, "got buffer, info: size=" + mBufferInfo.size
                    + ", presentationTimeUs=" + mBufferInfo.presentationTimeUs
                    + ", offset=" + mBufferInfo.offset);
        }
        if (encodedData != null) {
            encodedData.position(mBufferInfo.offset);
            encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
            mMuxer.writeSampleData(mVideoTrackIndex, encodedData, mBufferInfo); //混合器写文件
            Log.i(TAG, "sent " + mBufferInfo.size + " bytes to muxer...");
        }
    }

    private void resetOutputFormat() {
        // should happen before receiving buffers, and should only happen once
        if (mMuxerStarted) {
            throw new IllegalStateException("output format already changed!");
        }
        MediaFormat newFormat = mEncoder.getOutputFormat();

        Log.i(TAG, "output format changed.\n new format: " + newFormat.toString());
        mVideoTrackIndex = mMuxer.addTrack(newFormat);
        mMuxer.start();
        mMuxerStarted = true;
        Log.i(TAG, "started media muxer, videoIndex=" + mVideoTrackIndex);
    }

    private void sendAVCDecoderConfigurationRecord(long tms, MediaFormat format) {
        byte[] AVCDecoderConfigurationRecord = Packager.H264Packager.generateAVCDecoderConfigurationRecord(format);
        int packetLen = Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH +
                AVCDecoderConfigurationRecord.length;
        byte[] finalBuff = new byte[packetLen];
        Packager.FLVPackager.fillFlvVideoTag(finalBuff,
                0,
                true,
                true,
                AVCDecoderConfigurationRecord.length);
        System.arraycopy(AVCDecoderConfigurationRecord, 0,
                finalBuff, Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH, AVCDecoderConfigurationRecord.length);
        RESFlvData resFlvData = new RESFlvData();
        resFlvData.droppable = false;
        resFlvData.byteBuffer = finalBuff;
        resFlvData.size = finalBuff.length;
        resFlvData.dts = (int) tms;
        resFlvData.flvTagType = FLV_RTMP_PACKET_TYPE_VIDEO;
        resFlvData.videoFrameType = RESFlvData.NALU_TYPE_IDR;
        mDataCollecter.collect(resFlvData, FLV_RTMP_PACKET_TYPE_VIDEO);
    }

    private void sendRealData(long tms, ByteBuffer realData) {
        int realDataLength = realData.remaining();
        int packetLen = Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH +
                Packager.FLVPackager.NALU_HEADER_LENGTH +
                realDataLength;
        byte[] finalBuff = new byte[packetLen];
        realData.get(finalBuff, Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH +
                        Packager.FLVPackager.NALU_HEADER_LENGTH,
                realDataLength);
        int frameType = finalBuff[Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH +
                Packager.FLVPackager.NALU_HEADER_LENGTH] & 0x1F;
        Packager.FLVPackager.fillFlvVideoTag(finalBuff,
                0,
                false,
                frameType == 5,
                realDataLength);
        RESFlvData resFlvData = new RESFlvData();
        resFlvData.droppable = true;
        resFlvData.byteBuffer = finalBuff;
        resFlvData.size = finalBuff.length;
        resFlvData.dts = (int) tms;
        resFlvData.flvTagType = FLV_RTMP_PACKET_TYPE_VIDEO;
        resFlvData.videoFrameType = frameType;
        mDataCollecter.collect(resFlvData, FLV_RTMP_PACKET_TYPE_VIDEO);
    }
}
