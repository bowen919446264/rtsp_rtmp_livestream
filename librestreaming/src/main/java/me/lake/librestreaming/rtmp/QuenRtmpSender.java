package me.lake.librestreaming.rtmp;

import android.util.Log;
import me.lake.librestreaming.client.CallbackDelivery;
import me.lake.librestreaming.core.RESByteSpeedometer;
import me.lake.librestreaming.core.RESFrameRateMeter;
import me.lake.librestreaming.core.listener.RESConnectionListener;
import me.lake.librestreaming.model.RESCoreParameters;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Created by liuwb on 2016/11/30.
 */
public class QuenRtmpSender extends RESRtmpSender {

    BlockingDeque<RESFlvData> dataList;

    private FLvMetaData flvMetaData;

    private Thread senderThread;
    private RtmpSendThread sender;
    private int maxlength;

    @Override
    public void prepare(RESCoreParameters coreParameters) {
        synchronized (syncOp) {
            flvMetaData = new FLvMetaData(coreParameters);
            sender = new RtmpSendThread(flvMetaData);
            senderThread = new Thread(sender, "RtmpSendThread");
            maxlength = coreParameters.senderQueueLength;
            dataList = new LinkedBlockingDeque<>(maxlength * 2);

//            workHandlerThread = new HandlerThread("RESRtmpSender,workHandlerThread");
//            workHandlerThread.start();
//            workHandler = new WorkHandler(coreParameters.senderQueueLength,
//                    new FLvMetaData(coreParameters),
//                    workHandlerThread.getLooper());
        }
    }

    @Override
    public void setConnectionListener(RESConnectionListener connectionListener) {
        //TODO
        synchronized (syncOp) {
            sender.setConnectionListener(connectionListener);
        }
    }

    public String getServerIpAddr() {
        synchronized (syncOp) {
            return sender == null ? null : sender.getServerIpAddr();
        }
    }

    public float getSendFrameRate() {
        synchronized (syncOp) {
            return sender == null ? 0 : sender.getSendFrameRate();
        }
    }

    public float getSendBufferFreePercent() {
        synchronized (syncOp) {
            return dataList.size() / ((float) maxlength);
        }
    }

    public void start(String rtmpAddr) {
        synchronized (syncOp) {
            sender.sendStart(rtmpAddr);
            senderThread.start();
        }
    }

    public void feed(RESFlvData flvData, int type) {
        synchronized (syncOp) {
            try {
                dataList.putLast(flvData);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void stop() {
        synchronized (syncOp) {
            sender.sendStop();
        }
    }

    public void destroy() {
        synchronized (syncOp) {
            sender = null;
            /**
             * do not wait librtmp to quit
             */
        }
    }

    public int getTotalSpeed() {
        synchronized (syncOp) {
            if (sender != null) {
                return sender.getTotalSpeed();
            } else {
                return 0;
            }
        }
    }

    public class RtmpSendThread implements Runnable {
        private long jniRtmpPointer = 0;
        private String serverIpAddr = null;
        private FLvMetaData fLvMetaData;
        private final Object syncWriteMsgNum = new Object();
        private RESByteSpeedometer videoByteSpeedometer = new RESByteSpeedometer(TIMEGRANULARITY);
        private RESByteSpeedometer audioByteSpeedometer = new RESByteSpeedometer(TIMEGRANULARITY);
        private RESFrameRateMeter sendFrameRateMeter = new RESFrameRateMeter();
        private RESConnectionListener connectionListener;
        private final Object syncConnectionListener = new Object();

        private int errorTime = 0;
        private boolean isRun = false;
        private int latestDtsTime = 0;

        public RtmpSendThread(FLvMetaData fLvMetaData) {
            this.fLvMetaData = fLvMetaData;
        }

        public String getServerIpAddr() {
            return serverIpAddr;
        }

        public float getSendFrameRate() {
            return sendFrameRateMeter.getFps();
        }

        public void sendStart(String rtmpAddr) {
            isRun = true;
            sendFrameRateMeter.reSet();
            jniRtmpPointer = RtmpClient.open(rtmpAddr, true);
            final int openR = jniRtmpPointer == 0 ? 1 : 0;
            if (openR == 0) {
                serverIpAddr = RtmpClient.getIpAddr(jniRtmpPointer);
            }
            synchronized (syncConnectionListener) {
                if (connectionListener != null) {
                    CallbackDelivery.i().post(new Runnable() {
                        @Override
                        public void run() {
                            connectionListener.onOpenConnectionResult(openR);
                        }
                    });
                }
            }

            if (jniRtmpPointer != 0) {
                byte[] MetaData = fLvMetaData.getMetaData();
                RtmpClient.write(jniRtmpPointer,
                        MetaData,
                        MetaData.length,
                        RESFlvData.FLV_RTMP_PACKET_TYPE_INFO, 0);
            }
        }

        public void setConnectionListener(RESConnectionListener connectionListener) {
            synchronized (syncConnectionListener) {
                this.connectionListener = connectionListener;
            }
        }

        public void sendStop() {
            isRun = false;
            if (jniRtmpPointer == 0) {
                return;
            }
            errorTime = 0;
            final int closeR = RtmpClient.close(jniRtmpPointer);
            serverIpAddr = null;
            synchronized (syncConnectionListener) {
                if (connectionListener != null) {
                    CallbackDelivery.i().post(new Runnable() {
                        @Override
                        public void run() {
                            connectionListener.onCloseConnectionResult(closeR);
                        }
                    });
                }
            }
        }

        public int getTotalSpeed() {
            return getVideoSpeed() + getAudioSpeed();
        }

        public int getVideoSpeed() {
            return videoByteSpeedometer.getSpeed();
        }

        public int getAudioSpeed() {
            return audioByteSpeedometer.getSpeed();
        }

        @Override
        public void run() {
            while (isRun && jniRtmpPointer != 0) {
                RESFlvData flvData = null;
                try {
                    flvData = dataList.takeFirst();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (flvData == null) {
                    continue;
                }
                //在发送的时候微调时间间隔
                if (flvData.dts > latestDtsTime) {
                    latestDtsTime = flvData.dts;
                } else {
                    latestDtsTime++;
                }
                final int res = RtmpClient.write(jniRtmpPointer, flvData.byteBuffer, flvData.byteBuffer.length, flvData.flvTagType, latestDtsTime);
                if (res == 0) {
                    errorTime = 0;
                    if (flvData.flvTagType == RESFlvData.FLV_RTMP_PACKET_TYPE_VIDEO) {
                        videoByteSpeedometer.gain(flvData.size);
                        sendFrameRateMeter.count();
                    } else {
                        audioByteSpeedometer.gain(flvData.size);
                    }
                } else {
                    ++errorTime;
                    synchronized (syncConnectionListener) {
                        if (connectionListener != null) {
                            CallbackDelivery.i().post(new RESConnectionListener.RESWriteErrorRunable(connectionListener, res));
                        }
                    }
                }
            }
        }
    }
}
