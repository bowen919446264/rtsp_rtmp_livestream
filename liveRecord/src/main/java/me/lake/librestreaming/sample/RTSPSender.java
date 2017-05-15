package me.lake.librestreaming.sample;

import android.media.MediaCodec;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import com.pedro.rtsp.rtp.packets.AccPacket;
import com.pedro.rtsp.rtp.packets.H264Packet;
import com.pedro.rtsp.rtsp.Protocol;
import com.pedro.rtsp.rtsp.RtspClient;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;

import java.nio.ByteBuffer;

import me.lake.librestreaming.core.listener.RESConnectionListener;
import me.lake.librestreaming.model.RESCoreParameters;
import me.lake.librestreaming.rtsp.IRtspSender;

/**
 * Created by liuwb on 2017/4/26.
 */
public class RTSPSender implements IRtspSender, ConnectCheckerRtsp {
    private boolean isUseUDP;
    private RtspClient rtspClient;
    private AccPacket accPacket;
    private H264Packet h264Packet;
    private RESConnectionListener connectionListener;
    private Handler mainHander = new Handler(Looper.getMainLooper());

    private static RTSPSender instance;
    private static Object object = new Object();

    public static RTSPSender getInstance(boolean useUDP) {
        synchronized (object) {
            if (instance == null) {
                instance = new RTSPSender(useUDP);
            }
            return instance;
        }
    }

    public RTSPSender(boolean useUDP) {
        this.isUseUDP = useUDP;
        Protocol protocol = isUseUDP ? Protocol.UDP : Protocol.TCP;
        rtspClient = new RtspClient(this, protocol);
        accPacket = new AccPacket(rtspClient, protocol);
        h264Packet = new H264Packet(rtspClient, protocol);
    }

    @Override
    public void prepare(RESCoreParameters coreParameters) {

    }

    public void setAuthorization(String user, String password) {
        rtspClient.setAuthorization(user, password);
    }

    @Override
    public void onSetAudioSampleRate(int sampleRate) {
        rtspClient.setSampleRate(sampleRate);
        accPacket.setSampleRate(sampleRate);
    }

    @Override
    public void setConnectionListener(RESConnectionListener connectionListener) {
        this.connectionListener = connectionListener;
    }

    @Override
    public void onSPSandPPS(ByteBuffer sps, ByteBuffer pps) {
        byte[] mSPS = new byte[sps.capacity() - 4];
        sps.position(4);
        sps.get(mSPS, 0, mSPS.length);
        byte[] mPPS = new byte[pps.capacity() - 4];
        pps.position(4);
        pps.get(mPPS, 0, mPPS.length);

        String sSPS = Base64.encodeToString(mSPS, 0, mSPS.length, Base64.NO_WRAP);
        String sPPS = Base64.encodeToString(mPPS, 0, mPPS.length, Base64.NO_WRAP);
        rtspClient.setSPSandPPS(sSPS, sPPS);
        rtspClient.connect();
    }

    @Override
    public void onGetH264Data(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
        h264Packet.createAndSendPacket(h264Buffer, info);
    }

    @Override
    public void onGetAccData(ByteBuffer accBuffer, MediaCodec.BufferInfo info) {
        accPacket.createAndSendPacket(accBuffer, info);
    }

    @Override
    public void start(String rtspAddr) {
        rtspClient.setUrl(rtspAddr);
    }

    @Override
    public void stop() {
        rtspClient.disconnect();
        accPacket.close();
        h264Packet.close();
    }

    @Override
    public void destroy() {
        try {
            stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
        rtspClient = null;
        accPacket = null;
        h264Packet = null;
    }

    public void updateDestination() {
        accPacket.updateDestinationAudio();
        h264Packet.updateDestinationVideo();
    }

    //--------------------ConnectCheckerRtsp-------------
    @Override
    public void onConnectionSuccessRtsp() {
        Log.e("TAG", "onConnectionSuccessRtsp------------");
        mainHander.post(new Runnable() {
            @Override
            public void run() {
                updateDestination();
                if (connectionListener != null) {
                    connectionListener.onOpenConnectionResult(0);
                }
            }
        });

    }

    @Override
    public void onConnectionFailedRtsp() {
        Log.e("TAG", "onConnectionFailedRtsp------------");
        mainHander.post(new Runnable() {
            @Override
            public void run() {
                if (connectionListener != null) {
                    connectionListener.onOpenConnectionResult(1);
                }
            }
        });

    }

    @Override
    public void onDisconnectRtsp() {
        mainHander.post(new Runnable() {
            @Override
            public void run() {
                if (connectionListener != null) {
                    connectionListener.onCloseConnectionResult(0);
                }
            }
        });

    }

    @Override
    public void onAuthErrorRtsp() {
        Log.e("TAG", "onAuthErrorRtsp ------------- ");
        mainHander.post(new Runnable() {
            @Override
            public void run() {
                if (connectionListener != null) {
                    connectionListener.onOpenConnectionResult(1);
                }
            }
        });

    }

    @Override
    public void onAuthSuccessRtsp() {
        Log.e("TAG", "onAuthSuccessRtsp ------------- ");
    }

    @Override
    public void onWriteError() {
        mainHander.post(new Runnable() {
            @Override
            public void run() {
                if (connectionListener != null) {
                    connectionListener.onWriteError(9);
                }
            }
        });
    }
}
