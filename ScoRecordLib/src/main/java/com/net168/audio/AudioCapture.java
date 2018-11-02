package com.net168.audio;

import android.util.Log;
import com.net168.audio.audiorecord.AudioRecordCore;

/**
 * PCM音频录制
 *
 *
 * sample:
 mAudioCapture = new AudioCapture(AudioCapture.AUDIO_SAMPLE_RATE_44_1,
 AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

 if (mAudioCapture.getState() == AudioCapture.STATE_IDLE) {
     mAudioCapture.setAudioCaptureCallback(this);
     mAudioCapture.start();
 } else {
    mAudioCapture.release();
 }

 mAudioCapture.stop();
 mAudioCapture.release();
 */
public class AudioCapture {

    private static final String TAG = AudioCapture.class.getSimpleName();

    /**
     * 采样频率
     */
    public static final int AUDIO_SAMPLE_RATE_8 = 8000;
    public static final int AUDIO_SAMPLE_RATE_11_025 = 11025;
    public static final int AUDIO_SAMPLE_RATE_12 = 12000;
    public static final int AUDIO_SAMPLE_RATE_16 = 16000;
    public static final int AUDIO_SAMPLE_RATE_22_05 = 22050;
    public static final int AUDIO_SAMPLE_RATE_24 = 24000;
    public static final int AUDIO_SAMPLE_RATE_32 = 32000;
    public static final int AUDIO_SAMPLE_RATE_44_1 = 44100;
    public static final int AUDIO_SAMPLE_RATE_48 = 48000;
    public static final int AUDIO_SAMPLE_RATE_64 = 64000;
    public static final int AUDIO_SAMPLE_RATE_82 = 82000;
    public static final int AUDIO_SAMPLE_RATE_96 = 96000;
    public static final int AUDIO_SAMPLE_RATE_192 = 192000;

    /**
     * 录制器状态
     */
    public static final int STATE_UNINIT = 0;
    public static final int STATE_IDLE = 1;
    public static final int STATE_RECORDING = 2;


    private AudioRecordCore mCore;

    /**
     * 初始化录制器
     *
     * @param sampleRate 采样频率 参数见AudioCapture
     * @param channelConfig 采样声道摸个 参数见AudioCapture
     * @param audioFormat 采样格式 参数见AudioCapture
     */
    public AudioCapture(final int sampleRate, final int channelConfig, final int audioFormat) {

        mCore = new AudioRecordCore();
        boolean result = mCore.createRecord(getAudioRecordSampleRate(sampleRate), channelConfig, audioFormat);
        //如果createRecord不成功，认为初始化失败
        if (!result) {
            Log.e(TAG, "AudioRecordCore create record error");
            mCore.releaseRecord();
            mCore = null;
        }
    }

    /**
     * 开始录制器工作
     */
    public void start() {
        if (mCore == null) {
            Log.e(TAG, "AudioRecordCore not init");
            return;
        }
        mCore.startRecord();
    }

    /**
     * 暂停录制器工作
     */
    public void stop() {
        if (mCore == null) {
            Log.e(TAG, "AudioRecordCore not init");
            return;
        }
        mCore.stopRecord();
    }

    /**
     * 释放录制器资源，重新start()需要先init()
     */
    public void release() {
        if (mCore == null) {
            Log.e(TAG, "AudioRecordCore not init");
            return;
        }
        mCore.releaseRecord();
    }

    /**
     * 设置录制器PCM数据回调
     */
    public void setAudioCaptureCallback(final AudioCaptureCallback callback) {
        if (callback != null) {
            if (mCore == null) {
                Log.e(TAG, "AudioRecordCore not init");
                return;
            }
            mCore.setOnAudioCaptureCallback(new AudioRecordCore.InnerAudioCaptureCallback() {
                @Override
                public void onPCMDataAvailable(byte[] data, int size) {
                    callback.onPCMDataAvailable(data, size);
                }
            });
        }
    }

    /**
     * 获取录制器当前状态
     *
     * @return 参看AudioCapture
     */
    public int getState() {
        int state = AudioCapture.STATE_UNINIT;
        if (mCore == null) {
            state = AudioCapture.STATE_UNINIT;
        } else if (mCore.isRecording()) {
            state = AudioCapture.STATE_RECORDING;
        } else if (mCore.isInitSuccess()) {
            state = AudioCapture.STATE_IDLE;
        }
        return state;
    }

    /**
     * 将AudioCapture的采样频率转为AudioRecord支持的采样频率格式
     */
    private static int getAudioRecordSampleRate(final int sampleRate) {
        if (sampleRate == AudioCapture.AUDIO_SAMPLE_RATE_8 || sampleRate == AudioCapture.AUDIO_SAMPLE_RATE_11_025
            || sampleRate == AudioCapture.AUDIO_SAMPLE_RATE_12 || sampleRate == AudioCapture.AUDIO_SAMPLE_RATE_16
            || sampleRate == AudioCapture.AUDIO_SAMPLE_RATE_22_05 || sampleRate == AudioCapture.AUDIO_SAMPLE_RATE_24
            || sampleRate == AudioCapture.AUDIO_SAMPLE_RATE_32 || sampleRate == AudioCapture.AUDIO_SAMPLE_RATE_44_1
            || sampleRate == AudioCapture.AUDIO_SAMPLE_RATE_48 || sampleRate == AudioCapture.AUDIO_SAMPLE_RATE_64
            || sampleRate == AudioCapture.AUDIO_SAMPLE_RATE_82 || sampleRate == AudioCapture.AUDIO_SAMPLE_RATE_96
            || sampleRate == AudioCapture.AUDIO_SAMPLE_RATE_192) {
            return sampleRate;
        } else {
            return AudioCapture.AUDIO_SAMPLE_RATE_16;
        }
    }

    public interface AudioCaptureCallback {

        void onPCMDataAvailable(byte[] data, int size);
    }
}
