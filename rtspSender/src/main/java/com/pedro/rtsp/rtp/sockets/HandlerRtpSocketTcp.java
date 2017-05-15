package com.pedro.rtsp.rtp.sockets;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.pedro.rtsp.rtcp.SenderReportTcp;
import com.pedro.rtsp.utils.RtpConstants;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Semaphore;

/**
 * Created by liuwb on 2017/4/27.
 */
public class HandlerRtpSocketTcp extends RtpSocketTcp {

    private SocketData socketData;
    private final Object syncOp = new Object();
    private WorkHandler workHandler;
    private HandlerThread workHandlerThread;
    private int ssrc;

    public HandlerRtpSocketTcp() {
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
        socketData = new SocketData();
        socketData.byteBuffer[1] &= 0x7F;
        setLong(socketData.byteBuffer, ssrc, 8, 12);
        return socketData.byteBuffer;
    }

    protected void resetFifo() {
        mCount = 0;
        mBufferIn = 0;
        mBufferOut = 0;
        mTimestamps = new long[mBufferCount];
        mBufferRequested = new Semaphore(mBufferCount);
        mBufferCommitted = new Semaphore(0);
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
        senderReportTcp.setSSRC(ssrc);
    }

    @Override
    public void commitBuffer(int length) throws IOException {
        updateSequence();
        socketData.dataLength = length;
        synchronized (syncOp) {
            workHandler.sendFood(socketData);
        }
    }

    @Override
    public void close() {
        super.close();
        stop();
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

    public long getClock() {
        return mClock;
    }

    public SenderReportTcp getSenderReportTcp() {
        return senderReportTcp;
    }

    public OutputStream getOutputStream() {
        return mOutputStream;
    }

    public byte[] getTcpHeader() {
        return mTcpHeader;
    }

    class SocketData {
        byte[] byteBuffer;
        long timestamp;
        /**
         * 标记是音频还是视频
         */
        int dataType;

        int dataLength;

        public SocketData() {
            byteBuffer = new byte[RtpConstants.MTU];
            byteBuffer[0] = (byte) Integer.parseInt("10000000", 2);
            byteBuffer[1] = (byte) RtpConstants.playLoadType;
            dataType = AUDIO_DATA_TYPE;
        }
    }

    protected static class WorkHandler extends Handler {
        private final static String TAG = "WorkHandler";
        private final static int MSG_WRITE = 2;
        private int maxQueueLength;
        private int writeMsgNum = 0;
        private final Object syncWriteMsgNum = new Object();
        private int mCount = 0;
        private HandlerRtpSocketTcp tcpSockent;
        private long timeSampe = 0;

        WorkHandler(int maxQueueLength, HandlerRtpSocketTcp tcpSockent, Looper looper) {
            super(looper);
            this.maxQueueLength = maxQueueLength;
            this.tcpSockent = tcpSockent;
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
                    SocketData sendData = (SocketData) msg.obj;
                    if (writeMsgNum >= (maxQueueLength * 2 / 3) && sendData.dataType == BaseRtpSocket.VIDEO_DATA_TYPE) {
                        Log.e(TAG, "write abandon packet, list size == " + writeMsgNum);
                        break;
                    }
                    long currentTimeSampe = (sendData.timestamp / 100L) * (tcpSockent.getClock() / 1000L) / 10000L;
                    tcpSockent.getSenderReportTcp().update(sendData.dataLength,
                            currentTimeSampe);
                    if (mCount++ > 30) {
                        Log.i(TAG, "send packet, " + sendData.dataLength + " Size");
                        try {
                            sendTCP(sendData);
                        } catch (Exception e) {
                            e.printStackTrace();
                            tcpSockent.postSendError();
                            Log.e("TAG", "TCP 发送包失败-----------");
                        }
                    }
                    break;
            }

        }

        private void sendTCP(SocketData sendData) throws Exception {
            synchronized (tcpSockent.getOutputStream()) {
                byte[] mTcpHeader = tcpSockent.getTcpHeader();
                int len = sendData.dataLength;
                mTcpHeader[2] = (byte) (len >> 8);
                mTcpHeader[3] = (byte) (len & 0xFF);
                tcpSockent.getOutputStream().write(mTcpHeader);
                tcpSockent.getOutputStream().write(sendData.byteBuffer, 0, len);
                tcpSockent.getOutputStream().flush();
                Log.d(TAG, "send " + len);
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
                tcpSockent.getSenderReportTcp().reset();
            }
        }

        public void sendFood(SocketData sendData) {
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
