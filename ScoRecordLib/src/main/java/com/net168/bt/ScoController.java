package com.net168.bt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.CountDownTimer;
import android.util.Log;
import java.util.HashSet;
import java.util.Set;

/**
 * Sco操作
 * <p>
 *     sco打开:通过蓝牙耳机mic录音<br/>
 *     sco关闭:通过手机mic录音<br/>
 * <p/>
 *
 * @author 喵叔catuncle    11/2/18
 */
public class ScoController {

    private static final String TAG = ScoController.class.getSimpleName();
    private final Context mContext;
    private final BluetoothAdapter mBluetoothAdapter;
    private final AudioManager mAudioManager;
    private final HeadsetReceiver headsetReceiver;

    private final Set<ScoCallback> scoCallbacks = new HashSet<>();
    private boolean mIsCountDownOn;
    private boolean mIsStarting;
    private boolean mIsStarted;

    public ScoController(Context mContext) {
        this(mContext, null);
    }

    public ScoController(Context mContext, ScoCallback scoCallback) {
        this.mContext = mContext.getApplicationContext();
        if (scoCallback != null) {
            this.scoCallbacks.add(scoCallback);
        }
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        headsetReceiver = new HeadsetReceiver();
    }

    /**
     * sco打开
     */
    public boolean start() {
        if (!mIsStarted) {
            Log.i(TAG, "start");
            mIsStarted = true;

            mIsStarted = startBluetooth();
        }

        return mIsStarted;
    }

    /**
     * sco关闭
     */
    public void stop() {
        Log.i(TAG, "stop");
        mIsStarted = false;

        stopBluetooth();
    }

    public void addListener(ScoCallback scoCallback) {
        if (scoCallback != null) {
            this.scoCallbacks.add(scoCallback);
        }
    }

    /**
     * 判断sco是否已连接
     */
    public boolean isScoConnected() {
        return isHeadsetConnected() && mAudioManager.isBluetoothScoOn();
    }


    /**
     * 判断headset是否已连接
     */
    private boolean isHeadsetConnected() {
        boolean connected = false;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            AudioDeviceInfo[] devices = mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
            for (AudioDeviceInfo device : devices) {
                int type = device.getType();
                Log.i(TAG, "AudioDeviceInfo.type[" + type + "]");
                if (type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                    connected = true;
                    break;
                }
            }
        } else {
            connected = mAudioManager.isBluetoothA2dpOn();
        }

        return connected;
    }

    private boolean startBluetooth() {
        Log.i(TAG, "startBluetooth");

        // Device support bluetooth
        if (mBluetoothAdapter != null) {
            if (mAudioManager.isBluetoothScoAvailableOffCall()) {
                headsetReceiver.register(mContext, new HeadsetReceiver.HeadsetCallback() {

                    @Override
                    public void onHeadsetConnected(BluetoothDevice bluetoothDevice) {
                        Log.i(TAG, bluetoothDevice.getName() + " connected");
                        //start sco...
                        startSco();

                        if (scoCallback != null) {
                            scoCallback.onHeadsetConnected();
                        }
                    }

                    @Override
                    public void onHeadsetDisconnected(BluetoothDevice bluetoothDevice) {
                        Log.i(TAG, bluetoothDevice.getName() + " disconnected");
                        if (mIsCountDownOn) {
                            mIsCountDownOn = false;
                            mCountDown.cancel();
                        }
                        mAudioManager.setMode(AudioManager.MODE_NORMAL);

                        if (scoCallback != null) {
                            scoCallback.onHeadsetDisconnected();
                        }
                    }

                    @Override
                    public void onScoAudioRetry() {
                        startSco();
                    }

                    @Override
                    public void onScoAudioConnected() {
                        if (mIsStarting) {
                            mIsStarting = false;
                            if (scoCallback != null) {
                                scoCallback.onHeadsetConnected();
                            }
                        }

                        if (mIsCountDownOn) {
                            mIsCountDownOn = false;
                            mCountDown.cancel();
                        }

                        mAudioManager.setBluetoothScoOn(true);  //打开SCO
                        if (scoCallback != null) {
                            scoCallback.onScoAudioConnected();
                        }

                        Log.i(TAG, "Sco connected");
                    }

                    @Override
                    public void onScoAudioDisconnected() {
                        Log.i(TAG, "Sco disconnected");
                        if (!mIsStarting) {
//                            stopBluetooth();
                            if (scoCallback != null) {
                                scoCallback.onScoAudioDisconnected();
                            }
                        }
                    }
                });

                if (isHeadsetConnected()) {
//                    stopBluetooth();
                    //start sco...
                    startSco();
                    return true;
                }
            }
        }

        return false;
    }

    private void startSco() {
        //start sco...
        mAudioManager.setBluetoothScoOn(false);
        mAudioManager.stopBluetoothSco();
        mAudioManager.setMode(AudioManager.MODE_NORMAL);

        //蓝牙录音的关键，启动SCO连接，耳机话筒才起作用
        mIsCountDownOn = true;
        mCountDown.start();

    }

    private void stopBluetooth() {
        if (mIsCountDownOn) {
            mIsCountDownOn = false;
            mCountDown.cancel();
        }

        mAudioManager.setBluetoothScoOn(false);
        mAudioManager.stopBluetoothSco();
        mAudioManager.setMode(AudioManager.MODE_NORMAL);
    }


    /**
     * Try to connect to audio headset in onTick.
     */
    private CountDownTimer mCountDown = new CountDownTimer(10000, 1000) {

        @SuppressWarnings("synthetic-access")
        @Override
        public void onTick(long millisUntilFinished) {
            // When this call is successful, this count down timer will be canceled.
            mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            mAudioManager.startBluetoothSco();

            Log.i(TAG, "onTick start bluetooth Sco");
        }

        @SuppressWarnings("synthetic-access")
        @Override
        public void onFinish() {
            // Calls to startBluetoothSco() in onStick are not successful.
            // Should implement something to inform user of this failure
            mIsCountDownOn = false;
            mAudioManager.setMode(AudioManager.MODE_NORMAL);

            Log.i(TAG, "onFinish fail to connect to headset audio");
        }
    };

    private final ScoCallback scoCallback = new ScoCallback() {
        @Override
        public void onHeadsetConnected() {
            for (ScoCallback scoCallback : scoCallbacks) {
                scoCallback.onHeadsetConnected();
            }
        }

        @Override
        public void onHeadsetDisconnected() {
            for (ScoCallback scoCallback : scoCallbacks) {
                scoCallback.onHeadsetDisconnected();
            }
        }

        @Override
        public void onScoAudioConnected() {
            for (ScoCallback scoCallback : scoCallbacks) {
                scoCallback.onScoAudioConnected();
            }
        }

        @Override
        public void onScoAudioDisconnected() {
            for (ScoCallback scoCallback : scoCallbacks) {
                scoCallback.onScoAudioDisconnected();
            }
        }
    };

    public interface ScoCallback {

        void onHeadsetConnected();

        void onHeadsetDisconnected();

        void onScoAudioConnected();

        void onScoAudioDisconnected();
    }

}