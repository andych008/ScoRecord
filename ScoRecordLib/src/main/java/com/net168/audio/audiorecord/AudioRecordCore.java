package com.net168.audio.audiorecord;

import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.MediaRecorder.AudioSource;
import android.util.Log;
import com.net168.audio.AudioCapture;

public class AudioRecordCore {

    public static final String TAG = AudioRecordCore.class.getSimpleName();
    private final static int UNINIT = 0;
    private final static int INIT = 1;
    private final static int RECORDING = 2;

    private int mState = 0;  // 0 - uninit   1 - init   2 - recording
    private AudioRecord mAudioRecord;
    private int mRecordBufSize; //缓存区大小
    private byte mPcmData[];  //缓存内存区域

    private InnerAudioCaptureCallback mCallback;

    public AudioRecordCore() {
    }

    public boolean createRecord(int sampleRate, int channelConfig, int audioFormat) {
        //防止多次初始化
        if (mState != UNINIT) {
            return true;
        }
        //获取最低AudioRecord内部音视频缓冲区大小，此大小依赖于各产商实现，最好不要自己计算
        mRecordBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        //初始化AudioRecord实例
        mAudioRecord = new AudioRecord(AudioSource.MIC, sampleRate, channelConfig, audioFormat, mRecordBufSize);
        //检测AudioRecord初始化是否成功
        if (mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.i(TAG, "init AudioRecord fail，err code：" + mAudioRecord.getState());
            mAudioRecord = null;
            mRecordBufSize = 0;
            return false;
        } else {
            //创建一个数据缓冲区
            Log.i(TAG, "init AudioRecord success.");
            //创建一个位置用于存放后续的PCM数据
            mPcmData = new byte[mRecordBufSize];
            mState = INIT;
            return true;
        }
    }

    /**
     * 获取缓存区大小，一般来说，回调返回的pcm缓存区大小不会大于这个数值
     */
    public int getMaxBufferSize() {
        return mRecordBufSize;
    }


    public void startRecord() {
        //确认状态是否待录制
        if (mState != INIT) {
            Log.w(TAG, "startRecord fail, because the state is not init");
            return;
        }
        mState = RECORDING;
        //启动音频录制需求
        Log.i(TAG, "set AudioRecord recording.");
        mAudioRecord.startRecording();
        //随后要启动子线程去循环读取数据
        mReadDataThread.start();
    }

    public void stopRecord() {
        //确认状态是否正在录制
        if (mState != RECORDING) {
            Log.w(TAG, "startRecord fail, because the state is not recording");
            return;
        }
        mState = INIT;
        mAudioRecord.stop();
    }

    /**
     * 恢复状态到刚执行构造函数状态
     */
    public void releaseRecord() {
        mState = UNINIT;
        mAudioRecord.release();
        mAudioRecord = null;
        mPcmData = null;
        mRecordBufSize = 0;
        mCallback = null;
    }

    /**
     * 包含已经初始化完毕，或者正在录制则为true
     */
    public boolean isInitSuccess() {
        return mState != UNINIT;
    }

    /**
     * 只包含正在录制的状态
     */
    public boolean isRecording() {
        return mState == RECORDING;
    }

    public void setOnAudioCaptureCallback(InnerAudioCaptureCallback callback) {
        //设置线程与采集线程不是同一个，存在并发，所以需要加个同步控制
        synchronized (AudioCapture.class) {
            mCallback = callback;
        }
    }

    private Thread mReadDataThread = new Thread() {
        @Override
        public void run() {
            int read;
            Log.i(TAG, "start record looper.");
            while (mState == RECORDING) {
                //读取mRecordBufSize长度的音频数据存入mPcmData中
                read = mAudioRecord.read(mPcmData, 0, mRecordBufSize);
                //如果读取音频数据没有出现错误 ===> read 大于0
                if (read >= AudioRecord.SUCCESS) {
                    Log.v(TAG, "read raw pcm data, size is " + read);
                    synchronized (AudioCapture.class) {
                        if (mCallback != null) {
                            mCallback.onPCMDataAvailable(mPcmData, read);
                        }
                    }
                } else {
                    Log.w(TAG, "read data with err code = " + read);
                }
            }
        }
    };

    public interface InnerAudioCaptureCallback {

        void onPCMDataAvailable(byte[] data, int size);
    }
}
