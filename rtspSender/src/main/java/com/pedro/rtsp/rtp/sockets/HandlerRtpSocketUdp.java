package com.pedro.rtsp.rtp.sockets;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.pedro.rtsp.utils.RtpConstants;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Created by liuwb on 2017/5/3.
 */
public class HandlerRtpSocketUdp extends RtpSocketUdp {
    private UdpSocketData socketData;
    private final Object syncOp = new Object();
    private WorkHandler workHandler;
    private HandlerThread workHandlerThread;
    private int ssrc;

    private String mDest;

    public HandlerRtpSocketUdp() {
        super();
        synchronized (syncOp) {
            workHandlerThread = new HandlerThread("HandlerRtpSocketTcp,workHandlerThread");
            workHandlerThread.start();
            workHandler = new WorkHandler(mBufferCount, this,
                    workHandlerThread.getLooper());
        }
    }

    @Override
    public void setSendDataType(int dataType) {
        super.setSendDataType(dataType);
        socketData.dataType = dataType;
    }

    @Override
    public byte[] requestBuffer() throws InterruptedException {
        socketData = new UdpSocketData();
        socketData.byteBuffer[1] &= 0x7F;
        setLong(socketData.byteBuffer, ssrc, 8, 12);
        if (mPort != -1) {
            socketData.packet.setPort(mPort);
        }
        if (!TextUtils.isEmpty(mDest)) {
            try {
                socketData.packet.setAddress(InetAddress.getByName(mDest));
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }
        return socketData.byteBuffer;
    }

    @Override
    protected void updateSequence() {
        setLong(socketData.byteBuffer, ++mSeq, 2, 4);
    }

    @Override
    public void updateTimestamp(long timestamp) {
        socketData.timestamp = timestamp;
        setLong(socketData.byteBuffer, (timestamp / 100L) * (mClock / 1000L) / 10000L, 4, 8);
    }

    @Override
    public void markNextPacket() {
        socketData.byteBuffer[1] |= 0x80;
    }

    @Override
    public void setSSRC(int ssrc) {
        this.ssrc = ssrc;
        senderReportUdp.setSSRC(ssrc);
    }

    @Override
    public void commitBuffer(int length) throws IOException {
        updateSequence();
        socketData.dataLength = length;
        socketData.packet.setLength(length);
        synchronized (syncOp) {
            workHandler.sendFood(socketData);
        }
    }

    @Override
    public void setDestination(String dest, int dport, int rtcpPort) {
        if (socketData != null) {
            if (dport != 0 && rtcpPort != 0) {
                socketData.packet.setPort(dport);
                this.mPort = dport;
                this.mDest = dest;
            }
            try {
                socketData.packet.setAddress(InetAddress.getByName(dest));
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }
        super.setDestination(dest, dport, rtcpPort);
    }

    @Override
    public void close() {
        stop();
        super.close();
    }

    public void stop() {
        synchronized (syncOp) {
            resetFifo();
            workHandler.sendStop();
            workHandler.removeCallbacksAndMessages(null);
            workHandlerThread.quit();
        }
    }

    @Override
    public void run() {

    }

    protected void sendData(UdpSocketData data) {
        senderReportUdp.update(data.packet.getLength(),
                (data.timestamp / 100L) * (mClock / 1000L) / 10000L, mPort);
        if (mCount++ > 30) {
            Log.i(TAG, "send packet, "
                    + data.packet.getLength()
                    + " Size, "
                    + data.packet.getPort()
                    + " Port");
            try {
                if (mSocket != null && data.packet.getPort() != -1) {
                    mSocket.send(data.packet);
                } else {
                    postSendError();
                }
            } catch (IOException e) {
                e.printStackTrace();
                postSendError();
            }
        }
    }

    private void sendStop() {
        senderReportUdp.reset();
    }

    class UdpSocketData {
        byte[] byteBuffer;
        DatagramPacket packet;
        long timestamp;
        /**
         * 标记是音频还是视频
         */
        int dataType;

        int dataLength;

        public UdpSocketData() {
            byteBuffer = new byte[RtpConstants.MTU];
            byteBuffer[0] = (byte) Integer.parseInt("10000000", 2);
            byteBuffer[1] = (byte) RtpConstants.playLoadType;
            dataType = AUDIO_DATA_TYPE;
            packet = new DatagramPacket(byteBuffer, 1);
        }
    }

    protected static class WorkHandler extends Handler {
        private final static String TAG = "WorkHandler";
        private final static int MSG_WRITE = 2;
        private int maxQueueLength;
        private int writeMsgNum = 0;
        private final Object syncWriteMsgNum = new Object();
        private int mCount = 0;
        private HandlerRtpSocketUdp udpSockent;
        private long timeSampe = 0;

        WorkHandler(int maxQueueLength, HandlerRtpSocketUdp udpSockent, Looper looper) {
            super(looper);
            this.maxQueueLength = maxQueueLength;
            this.udpSockent = udpSockent;
            sendStart();
        }

        public float getSendBufferFreePercent() {
            synchronized (syncWriteMsgNum) {
                float res = (float) (maxQueueLength - writeMsgNum) / (float) maxQueueLength;
                return res <= 0 ? 0f : res;
            }
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_WRITE:
                    synchronized (syncWriteMsgNum) {
                        --writeMsgNum;
                    }
                    UdpSocketData sendData = (UdpSocketData) msg.obj;
                    if (writeMsgNum >= (maxQueueLength * 2 / 3) && sendData.dataType == BaseRtpSocket.VIDEO_DATA_TYPE) {
                        Log.e(TAG, "write abandon packet, list size == " + writeMsgNum);
                        break;
                    }
                    udpSockent.sendData(sendData);
                    break;
            }

        }

        private void sendStart() {
            synchronized (syncWriteMsgNum) {
                this.removeMessages(MSG_WRITE);
                writeMsgNum = 0;
                mCount = 0;
                timeSampe = 0;
            }
        }

        public void sendStop() {
            synchronized (syncWriteMsgNum) {
                this.removeMessages(MSG_WRITE);
                writeMsgNum = 0;
                mCount = 0;
                timeSampe = 0;
                udpSockent.sendStop();
            }
        }

        public void sendFood(UdpSocketData sendData) {
            synchronized (syncWriteMsgNum) {
                //LAKETODO optimize
                if (writeMsgNum <= maxQueueLength) {
                    this.sendMessage(this.obtainMessage(MSG_WRITE, 0, 0, sendData));
                    ++writeMsgNum;
                } else {
                    Log.e("TAG", "abandon packet, list size == " + writeMsgNum);
                }
            }
        }
    }
}
