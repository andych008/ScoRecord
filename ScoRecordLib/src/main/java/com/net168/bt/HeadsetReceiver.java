package com.net168.bt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAssignedNumbers;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.util.Log;
import java.lang.reflect.Method;

/**
 * 监听蓝牙耳机状态变化
 *
 * @author dongwen.wang    8/24/18
 */
class HeadsetReceiver extends BroadcastReceiver {

    private static final String TAG = HeadsetReceiver.class.getSimpleName();
    private Context mContext = null;
    private HeadsetCallback headsetCallback;

    public boolean register(Context context, HeadsetCallback headsetCallback) {
        if (context != null && BluetoothAdapter.getDefaultAdapter() != null) {
            //防止重复注册
            if (!context.equals(mContext)) {
                unregister();
                this.headsetCallback = headsetCallback;
                this.mContext = context;
                IntentFilter filter = new IntentFilter();
                //手机蓝牙状态改变
                filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
                //蓝牙设备状态变化
                filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
                filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
                filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
                //Headset状态变化
                filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
                //A2DP状态变化
                filter.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
                //sco状态变化
                filter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
                //Headset电量等变化
                //filter.addAction(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT);


                //aaron
                IntentFilter headsetFilter=new IntentFilter();
                headsetFilter.addAction(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT);
                //filter.addCategory(BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_COMPANY_ID_CATEGORY+"."+BluetoothAssignedNumbers.GOOGLE);
                headsetFilter.addCategory(BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_COMPANY_ID_CATEGORY+"."+BluetoothAssignedNumbers.GOOGLE);



                context.registerReceiver(this, filter);
                context.registerReceiver(this, headsetFilter);
                return true;
            }
        }
        return false;
    }

    public void unregister() {
        headsetCallback = null;
        if (mContext != null) {
            mContext.unregisterReceiver(this);
            mContext = null;
        }
    }

    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
            int btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
            printBTState(btState);
        } else if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
            printACLState(action);
            BluetoothDevice mConnectedHeadset = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            BluetoothClass bluetoothClass = mConnectedHeadset.getBluetoothClass();
            if (bluetoothClass != null) {
                // Check if device is a headset. Besides the 2 below, are there other
                // device classes also qualified as headset?
                int deviceClass = bluetoothClass.getDeviceClass();
                if (deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE
                    || deviceClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET) {
                    if (headsetCallback != null) {
                        headsetCallback.onAclConnected();
                    }
                }
            }
        } else if (action.equals(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)) {
            int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
            BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            printHeadsetState(state, bluetoothDevice);
            switch (state) {
                case BluetoothProfile.STATE_DISCONNECTED:
                    if (headsetCallback != null) {
                        headsetCallback.onHeadsetDisconnected(bluetoothDevice);
                    }
                    break;
                case BluetoothProfile.STATE_CONNECTED:
                    if (headsetCallback != null) {
                        headsetCallback.onHeadsetConnected(bluetoothDevice);
                    }
                    break;
            }


        } else if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
            printACLState(action);
            if (headsetCallback != null) {
                headsetCallback.onAclDisconnected();
            }
        } else if (action.equals(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)) {
            Log.i(TAG, "onReceive with: a2dp intent = " + intent + "");
            int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
            printA2DPState(state);
            switch (state) {
                case BluetoothHeadset.STATE_AUDIO_DISCONNECTED:
                    if (headsetCallback != null) {
                        headsetCallback.onA2DPDisconnected();
                    }
                    break;
                case BluetoothHeadset.STATE_AUDIO_CONNECTED:
                    if (headsetCallback != null) {
                        headsetCallback.onA2DPConnected();
                    }
                    break;
            }
        } else if (action.equals(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)) {
            int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_ERROR);
            printScoState(state);
            if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                if (headsetCallback != null) {
                    //容错处理：如果上次打开sco没有关闭。再次打开会直接走到CONNECTED。正常应该是：CONNECTING--->CONNECTED
                    if (scoState == AudioManager.SCO_AUDIO_STATE_CONNECTING) {
                        headsetCallback.onScoAudioConnected();
                    } else {
                        headsetCallback.onScoAudioRetry();
                    }
                }
            } else if (state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
                if (headsetCallback != null) {
                    headsetCallback.onScoAudioDisconnected();
                }
            }
            scoState = state;
        } else if (action.equals(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT)) {
            String command = intent.getStringExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD);
            Log.i(TAG, "command =   "+command);
            int commandType = intent.getIntExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD_TYPE, -1);
            Log.i(TAG, "commandType =   "+commandType);
            if ("+IPHONEACCEV".equals(command)) {
                Object[] args = (Object[]) intent.getSerializableExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS);
                if (args.length >= 3 && args[0] instanceof Integer && ((Integer)args[0])*2+1<=args.length) {
                    for (int i=0;i<((Integer)args[0]);i++) {
                        if (!(args[i*2+1] instanceof Integer) || !(args[i*2+2] instanceof Integer)) {
                            continue;
                        }
                        if (args[i*2+1].equals(1)) {
                            float level = (((Integer)args[i*2+2])+1)/10.0f;
                            Log.i(TAG, "battery   "+level);
                            break;
                        }
                    }
                }
            }

        }
    }

    //打印蓝牙的状态
    private void printBTState(int btState) {
        switch (btState) {
            case BluetoothAdapter.STATE_OFF:
                Log.i(TAG, "BluetoothAdapter.ACTION_STATE_CHANGED========蓝牙:已关闭===========" + btState);
                break;
            case BluetoothAdapter.STATE_TURNING_OFF:
                Log.i(TAG, "BluetoothAdapter.ACTION_STATE_CHANGED========蓝牙:正在关闭==============" + btState);
                break;
            case BluetoothAdapter.STATE_TURNING_ON:
                Log.i(TAG, "BluetoothAdapter.ACTION_STATE_CHANGED========蓝牙:正在打开======" + btState);
                break;
            case BluetoothAdapter.STATE_ON:
                Log.i(TAG, "BluetoothAdapter.ACTION_STATE_CHANGED========蓝牙:已打开=========" + btState);
                break;
            default:
                break;
        }
    }

    //打印acl的状态
    private void printACLState(String action) {
        Log.i(TAG, "printACLState with: action = " + action + "");
    }
    /**
     * 通过反射 getBatteryLevel
     */
    public static int getBatteryLevel(BluetoothDevice bluetoothDevice) {
        try {
//            Method[] methods = bluetoothDevice.getClass().getMethods();
//            for (Method m : methods) {
//                Log.i(TAG, "getBatteryLevel with: m.getName() = " + m.getName() + "");
//            }
            Method method = bluetoothDevice.getClass().getMethod("getBatteryLevel");
            method.setAccessible(true);
            return (int) method.invoke(bluetoothDevice);
        } catch (Exception e) {
            return -1;
        }
    }

    private void printHeadsetState(int btState, BluetoothDevice bluetoothDevice) {
        switch (btState) {
            case BluetoothProfile.STATE_DISCONNECTED:
                Log.i(TAG, "BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED========Headset:已关闭==============" + btState + ", bluetoothDevice = " + bluetoothDevice.getName() + "");
                break;
            case BluetoothProfile.STATE_CONNECTING:
                Log.i(TAG, "BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED========Headset:打开中...==============" + btState + ", bluetoothDevice = " + bluetoothDevice.getName() + "");
                break;
            case BluetoothProfile.STATE_CONNECTED:
                Log.i(TAG, "BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED========Headset:已打开==============" + btState + ", bluetoothDevice = " + bluetoothDevice.getName() + "\n"
                +"getBatteryLevel = "+getBatteryLevel(bluetoothDevice));

                break;
            case BluetoothProfile.STATE_DISCONNECTING:
                Log.i(TAG, "BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED========Headset:关闭中...==============" + btState + ", bluetoothDevice = " + bluetoothDevice.getName() + "");
                break;
        }
    }

    private void printA2DPState(int btState) {
        switch (btState) {
            case BluetoothHeadset.STATE_AUDIO_DISCONNECTED:
                Log.i(TAG, "BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED========a2dp:已关闭==============" + btState);
                break;
            case BluetoothHeadset.STATE_AUDIO_CONNECTED:
                Log.i(TAG, "BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED========a2dp:已打开===========" + btState);
                break;
        }
    }

    private int scoState = AudioManager.SCO_AUDIO_STATE_DISCONNECTED;
    private void printScoState(int btState) {
        switch (btState) {
            case AudioManager.SCO_AUDIO_STATE_CONNECTING:
                Log.i(TAG, "AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED========sco:打开中...==============" + btState);
                break;
            case AudioManager.SCO_AUDIO_STATE_CONNECTED:
                Log.i(TAG, "AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED========sco:已打开===========" + btState);
                break;
            case AudioManager.SCO_AUDIO_STATE_DISCONNECTED:
                Log.i(TAG, "AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED========sco:已关闭==============" + btState);
                break;
            default:
                break;
        }
    }

    public static abstract class HeadsetCallback {

        public void onAclConnected() {
        }

        public void onAclDisconnected() {
        }

        abstract public void onHeadsetConnected(BluetoothDevice bluetoothDevice);

        abstract public void onHeadsetDisconnected(BluetoothDevice bluetoothDevice);

        public void onA2DPConnected() {
        }

        public void onA2DPDisconnected() {
        }

        abstract public void onScoAudioRetry();

        abstract public void onScoAudioConnected();

        abstract public void onScoAudioDisconnected();
    }
}