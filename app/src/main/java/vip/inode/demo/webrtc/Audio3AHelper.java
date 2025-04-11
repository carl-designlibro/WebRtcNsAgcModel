package vip.inode.demo.webrtc;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

//使用WebRtcNsAgcModel的噪声抑制和增益控制
//https://github.com/carl-designlibro/WebRtcNsAgcModel
public class Audio3AHelper {
    static final String TAG = "Audio3AHelper";

    //    自动增益控制
    private boolean enableAgc = true;

    //    噪声抑制
    private boolean enableAns = true;


    private AutomaticGainControlUtils agcUtils;
    private NoiseSuppressorUtils ansUtils;

    private long nsxId = 0;

    private long agcInst = 0;

    private int agcInstSize = 0;

    private int agcMinLevel = 0;

    private int agcMaxLevel = 255;

//    enum {
//        kAgcModeUnchanged,
//                kAgcModeAdaptiveAnalog,
//                kAgcModeAdaptiveDigital,
//                kAgcModeFixedDigital
//    };

    private int agcMode = 3;
    private short agcTargetLevelDbfs = 9;
    private short agcCompressionGaindB = 9;
    private boolean agcLimiterEnable = true;

    // |mode| = 0 is mild (6dB), |mode| = 1 is medium (10dB) and |mode| = 2 is
    // aggressive (15dB).
    private int ansMode = 2;

    //    ns:    (fs == 8000 || fs == 16000 || fs == 32000 || fs == 48000)
    private int sampleRate = 16000;

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
        checkSampleRateValid();
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public void updateSampleRate(int sampleRate) {
        if (sampleRate == this.sampleRate) {
            return;
        }
        free();
        setSampleRate(sampleRate);
        config();
    }

    public boolean isEnableAgc() {
        return enableAgc;
    }

    public void setEnableAgc(boolean enableAgc) {
        this.enableAgc = enableAgc;
    }

    public boolean isEnableAns() {
        return enableAns;
    }

    public void setEnableAns(boolean enableAns) {
        this.enableAns = enableAns;
    }


    public void config() {
        if (!isParamsValid()) {
            Log.e(TAG, "config error,params invalid");
            return;
        }
        if (enableAns) {
            configAns();
        }
        if (enableAgc) {
            configAgc();
        }
    }

    public void free() {
        freeAgc();
        freeAns();
    }

    private boolean isParamsValid() {
        if (!checkSampleRateValid()) {
            return false;
        }
        return true;
    }

    private boolean checkSampleRateValid() {
        return processSampleCount() > 0;
    }

    private void configAgc() {
        if (agcInst != 0 && agcUtils != null) {
            return;
        }
        agcUtils = new AutomaticGainControlUtils();
        agcInst = agcUtils.agcCreate();
        Log.i(TAG, "configAgc,agcInst=" + agcInst);

        int agcInitResult = agcUtils.agcInit(agcInst, agcMinLevel, agcMaxLevel, agcMode, sampleRate);
        if (agcInitResult != 0) {
            Log.e(TAG, "configAgc error,agcInitResult=" + agcInitResult);
            return;
        }
        int agcSetConfigResult = agcUtils.agcSetConfig(agcInst, agcTargetLevelDbfs, agcCompressionGaindB, agcLimiterEnable);
        if (agcSetConfigResult != 0) {
            Log.e(TAG, "configAgc error,agcSetConfigResult=" + agcSetConfigResult);
            return;
        }

    }

    private void freeAgc() {
        if (agcInst == 0 || agcUtils == null) {
            return;
        }
        agcUtils.agcFree(agcInst);
        agcInst = 0;
        agcUtils = null;
    }

    private void freeAns() {
        if (nsxId == 0 || ansUtils == null) {
            return;
        }
        ansUtils.nsxFree(nsxId);
        nsxId = 0;
        ansUtils = null;
    }

    private void configAns() {
        if (nsxId != 0 && ansUtils != null) {
            return;
        }
        ansUtils = new NoiseSuppressorUtils();
        nsxId = ansUtils.nsxCreate();
        int nsxInit = ansUtils.nsxInit(nsxId, sampleRate);
        if (nsxInit != 0) {
            Log.e(TAG, "configAns error,nsxInit=" + nsxInit);
            freeAns();
            return;
        }
        int nexSetPolicy = ansUtils.nsxSetPolicy(nsxId, ansMode);
        if (nexSetPolicy != 0) {
            Log.e(TAG, "configAns error,nexSetPolicy=" + nexSetPolicy);
            return;
        }
    }

    public boolean process(byte[] inframe, byte[] outframe) {
        assert inframe.length == outframe.length;
        assert inframe.length % 2 == 0;

        boolean ans = enableAns && nsxId != 0;
        boolean agc = enableAgc && agcInst != 0;

        if (!ans && !agc) {
            return false;
        }

        if (inframe.length != processSampleCount() * 2) {
            Log.e(TAG, "process error,inframe.length=" + inframe.length + ",processSampleCount=" + processSampleCount());
            return false;
        }

        boolean successed = false;

        short[] inputData = new short[inframe.length / 2];
        ByteBuffer byteBuffer = ByteBuffer.allocate(inframe.length);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        // 写入short数据
        for (byte value : inframe) {
            byteBuffer.put(value);
        }
        // 重置position以便读取
        byteBuffer.flip();
        byteBuffer.asShortBuffer().get(inputData);

        short[] outData = new short[inputData.length];
        successed = process(inputData, outData);
        if (successed) {
            // 将处理后的数据写入输出数组
            for (int i = 0; i < outData.length; i++) {
                outframe[i * 2] = (byte) (outData[i] & 0xFF);
                outframe[i * 2 + 1] = (byte) ((outData[i] >> 8) & 0xFF);
            }
        }
        return successed;
    }

    //    单次处理的采样数
//    sampleRate:
//    8000 -> 80
//    16000,32000,48000 -> 160
    public int processSampleCount() {
        if (sampleRate == 8000) {
            return 80;
        } else if (sampleRate == 16000 || sampleRate == 32000 || sampleRate == 48000) {
            return 160;
        }
        return 0;
    }

    public boolean process(short[] inframe, short[] outframe) {
        assert inframe.length == outframe.length;

        if (!enableAns && !enableAgc) {
            return false;
        }

        if (inframe.length != processSampleCount()) {
            Log.e(TAG, "process error,inframe.length=" + inframe.length + ",processSampleCount=" + processSampleCount());
            return false;
        }

        boolean successed = false;


        short[] inputData = Arrays.copyOf(inframe, inframe.length);

        if (enableAns && nsxId != 0) {
            int result = ansUtils.nsxProcess(nsxId, inputData, 1, outframe);
            successed = result == 0;
            if (successed) {
                inputData = Arrays.copyOf(outframe, outframe.length);
            }
        }

        if (enableAgc && agcInst != 0) {
            int result = agcUtils.agcProcess(agcInst, inputData, 1, inputData.length, outframe, 0, 0, 0, false);
            successed = result == 0;
        }
        return successed;
    }


    AudioTrack audioTrack;
    public void testPlay(Context context, byte[] audioData, int offsetInBytes, int sizeInBytes) {
        if (audioTrack == null) {
            AudioAttributes audioAttributes  = new AudioAttributes.Builder()
                    .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                    .build();

            AudioFormat audioFormat = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();

            int bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );

            AudioManager audioManager =
                    (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
            int sessionId = audioManager.generateAudioSessionId();

            audioTrack = new AudioTrack(
                    audioAttributes, audioFormat, bufferSize, AudioTrack.MODE_STREAM,
                    sessionId
            );
            audioTrack.play();
        }
        audioTrack.write(audioData, offsetInBytes, sizeInBytes);
    }
}
