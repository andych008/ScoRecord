package com.net168.audio;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

/**
 * PCM音频播放
 *
 * @author 喵叔catuncle    11/1/18
 */
public class AudioPlayer {

    private final static String TAG = AudioPlayer.class.getSimpleName();

    private AudioParam audioParam;// 音频参数

    private AudioTrack audioTrack;

    private volatile boolean threadExitFlag = true;            // 线程退出标志

    private volatile int playState = PlayState.MPS_UNINIT;      // 当前播放状态

    private PlayAudioThread playAudioThread; // 播放线程

    private IPlayCallback playCallback;

    public AudioPlayer(IPlayCallback playCallback) {
        this.playCallback = playCallback;
    }

    /*
     *  就绪播放源
     */
    public synchronized boolean prepare(AudioParam audioParam) {
        if (playState > PlayState.MPS_UNINIT) {
            return true;
        }
        if (audioParam == null) {
            return false;
        }

        this.audioParam = audioParam;
        try {
            createAudioTrack();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        setPlayState(PlayState.MPS_PREPARE);

        return true;
    }


    //播放音频（PCM）
    public void play(String filename) {
        Log.i(TAG, "play with: filename = " + filename + "");
        if (!threadExitFlag) {
            return;
        }
        threadExitFlag = false;
        playAudioThread = new PlayAudioThread(filename);
        playAudioThread.start();
    }

    public void play() {
        Log.i(TAG, "play");
        if (!threadExitFlag) {
            return;
        }

        threadExitFlag = false;
        playAudioThread = new PlayAudioThread();
        playAudioThread.start();
    }

    private final BlockingQueue<DataWrap> queue = new SynchronousQueue<>();

    public void write(byte[] data, int size) {
        if (!threadExitFlag) {
            try {
                //wait 100 ms  for the newest element.
                queue.offer(new DataWrap(data, size), 100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
            }
        }
    }

    public void stop() {
        threadExitFlag = true;
    }

    public boolean isPlaying() {
        return playState == PlayState.MPS_PLAYING;
    }

    private synchronized void setPlayState(int state) {
        this.playState = state;
    }

    private void createAudioTrack() throws Exception {
        // 获得构建对象的最小缓冲区大小
        int minBufSize = AudioTrack.getMinBufferSize(audioParam.rate,
            audioParam.channel,
            audioParam.sampleBit);

        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
            audioParam.rate,
            audioParam.channel,
            audioParam.sampleBit,
            minBufSize,
            AudioTrack.MODE_STREAM);

        /*
        简单来讲，采样率和比特率就像是坐标轴上的横纵坐标。
        横坐标的采样率表示了每秒钟的采样次数。显然，这个采样率越高，听到的声音和看到的图像就越连贯。
        纵坐标的比特率表示了用数字量来量化模拟量的时候的精度。


        sampleRateInHz：采样率

        channelConfig：声道
            AudioFormat.CHANNEL_OUT_MONO：输出单声道音频数据
            AudioFormat.CHANNEL_OUT_STEREO：输出双声道音频数据（立体声）
        audioFormat：音频数据格式
        mode：缓冲模式
            MODE_STATIC：一次性将音频载入以后再播放。这种方法对于铃声等内存占用较小，延时要求较高的声音来说很适用。
            MODE_STREAM：以流的形式，加载一点就播放一点。这个和我们在socket中发送数据一样，应用层从某个地方获取数据，例如通过编解码得到PCM数据，然后write到audiotrack。
        */
    }


    class PlayAudioThread extends Thread {

        private String filename;

        PlayAudioThread() {
        }

        PlayAudioThread(String filename) {
            this.filename = filename;
        }

        @Override
        public void run() {
            if (filename == null) {
                audioTrack.play();

                setPlayState(PlayState.MPS_PLAYING);

                while (true) {
                    if (threadExitFlag) {
                        break;
                    }
                    try {
                        DataWrap dataWrap = queue.poll(300, TimeUnit.MILLISECONDS);
                        if (dataWrap != null) {
                            audioTrack.write(dataWrap.data, 0, dataWrap.size);
                        } else {
                            break;
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            } else {
                DataInputStream dis = null;
                try {
                    //从音频文件中读取声音
                    dis = new DataInputStream(new BufferedInputStream(new FileInputStream(filename)));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                //最小缓存区
                int bufferSizeInBytes = AudioTrack
                    .getMinBufferSize(AudioCapture.AUDIO_SAMPLE_RATE_44_1, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                //创建AudioTrack对象   依次传入 :流类型、采样率（与采集的要一致）、音频通道（采集是IN 播放时OUT）、量化位数、最小缓冲区、模式

                byte[] data = new byte[bufferSizeInBytes];

                audioTrack.play();

                setPlayState(PlayState.MPS_PLAYING);

                while (true) {
                    if (threadExitFlag) {
                        break;
                    }

                    int i = 0;
                    try {
                        while (dis.available() > 0 && i < data.length) {
                            data[i] = dis.readByte();//录音时write Byte 那么读取时就该为readByte要相互对应
                            i++;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        break;
                    }
                    audioTrack.write(data, 0, data.length);

                    if (i != bufferSizeInBytes) //表示读取完了
                    {
                        break;
                    }
                }
            }

            audioTrack.stop();//停止播放
            audioTrack.release();//释放资源
            setPlayState(PlayState.MPS_UNINIT);
            if (playCallback != null) {
                playCallback.onPlayComplete();
            }
            threadExitFlag = true;
            Log.i(TAG, "PlayAudioThread complete...");
        }
    }

    private static final class DataWrap {

        DataWrap(byte[] data, int size) {
            this.data = data;
            this.size = size;
        }

        private byte[] data;
        private int size;
    }

    public static final class AudioParam {

        //new AudioParam(AudioCapture.AUDIO_SAMPLE_RATE_44_1, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        public AudioParam(int rate, int channel, int sampleBit) {
            this.rate = rate;
            this.channel = channel;
            this.sampleBit = sampleBit;
        }

        int rate;          // 采样率

        int channel;          // 声道

        int sampleBit;          // 采样精度
    }


    public interface PlayState {

        int MPS_UNINIT = 0;        // 未就绪

        int MPS_PREPARE = 1;      // 准备就绪(停止)

        int MPS_PLAYING = 2;      // 播放中

        int MPS_PAUSE = 3;        // 暂停
    }

    public interface IPlayCallback {

        void onPlayComplete();
    }

}