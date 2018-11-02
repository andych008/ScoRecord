package com.net168.audiorecorddemo;

import android.media.AudioFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.net168.audio.AudioCapture;
import com.net168.audio.AudioCapture.AudioCaptureCallback;
import com.net168.audio.AudioPlayer;
import com.net168.audio.AudioPlayer.AudioParam;
import com.net168.audio.AudioPlayer.IPlayCallback;
import com.net168.bt.ScoController;
import com.net168.bt.ScoController.ScoCallback;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private TextView mTipTv;
    private Button mStop;
    private Handler mainHandler;

    private ScoController scoController;
    private AudioCapture mAudioCapture;
    private AudioPlayer audioPlayer = new AudioPlayer(
        new IPlayCallback() {
            @Override
            public void onPlayComplete() {
                Log.i(TAG, "onPlayComplete");
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        scoController = ((App) getApplication()).getScoController();
        scoController.addListener(scoCallback);

        findViewById(R.id.start).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean ret = scoController.start();
                Log.i(TAG, "onClick with: ret = " + ret + "");
                if (!ret) {
                    Toast.makeText(MainActivity.this, "请先连接智能AI蓝牙耳机", Toast.LENGTH_SHORT).show();
                }
            }
        });
        findViewById(R.id.stop).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                scoController.stop();
            }
        });

        mStop = findViewById(R.id.record_stop);
        mStop.setOnClickListener(this);
        Button record_and_play = findViewById(R.id.record_and_play);
        record_and_play.setOnClickListener(this);
        mTipTv = findViewById(R.id.record_tip);

        mainHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.arg1 == 1) {
                    mTipTv.setText("使用蓝牙耳机-录制声音");
                } else {
                    mTipTv.setText("使用手机mic-录制声音");
                }
            }
        };
        setTip(scoController.isScoConnected());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.record_stop:
                mStop.setClickable(false);
                setTip(scoController.isScoConnected());
                stopRecord();
                break;
            case R.id.record_and_play:
                mStop.setClickable(true);
                recordAndPlay();
                break;
        }
    }

    private void setTip(boolean isConnected) {
        Message message = mainHandler.obtainMessage();
        if (isConnected) {
            message.arg1 = 1;
        } else {
            message.arg1 = 0;
        }
        mainHandler.sendMessage(message);
    }

    private void stopRecord() {
        mAudioCapture.stop();
        mAudioCapture.release();

    }


    private void recordAndPlay() {

        audioPlayer.prepare(new AudioParam(AudioCapture.AUDIO_SAMPLE_RATE_44_1, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT));

        mAudioCapture = new AudioCapture(AudioCapture.AUDIO_SAMPLE_RATE_44_1,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (mAudioCapture.getState() == AudioCapture.STATE_IDLE) {
            Log.i(TAG, "recordAndPlay 1");
            mAudioCapture.setAudioCaptureCallback(new AudioCaptureCallback() {
                @Override
                public void onPCMDataAvailable(byte[] data, int size) {
                    Log.i(TAG, "onPCMDataAvailable with: currentThread = " + Thread.currentThread().getName() + "");
                    //播放音频（PCM）
                    audioPlayer.write(data, size);
                }
            });
            mAudioCapture.start();

            audioPlayer.play();
        } else {
            Log.i(TAG, "recordAndPlay 2");
            mAudioCapture.release();
        }
    }


    private ScoCallback scoCallback = new ScoCallback() {

        @Override
        public void onHeadsetDisconnected() {
            Log.i(TAG, "Bluetooth headset disconnected");
        }

        @Override
        public void onHeadsetConnected() {
            Log.i(TAG, "Bluetooth headset connected");
        }

        @Override
        public void onScoAudioDisconnected() {
            Log.i(TAG, "Bluetooth sco audio finished");
            setTip(false);
            Toast.makeText(MainActivity.this, "蓝牙耳机不可录音!!!", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onScoAudioConnected() {
            Log.i(TAG, "Bluetooth sco audio started");
            setTip(true);
            Toast.makeText(MainActivity.this, "蓝牙耳机可录音", Toast.LENGTH_SHORT).show();
            recordAndPlay();
        }
    };
}
