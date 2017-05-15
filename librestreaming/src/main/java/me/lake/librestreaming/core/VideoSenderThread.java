package me.lake.librestreaming.core;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Pair;

import java.nio.ByteBuffer;

import me.lake.librestreaming.rtmp.RESFlvData;
import me.lake.librestreaming.rtmp.RESFlvDataCollecter;
import me.lake.librestreaming.rtmp.RESRtmpSender;
import me.lake.librestreaming.rtsp.IRtspSender;
import me.lake.librestreaming.tools.LogTools;

/**
 * Created by lakeinchina on 26/05/16.
 */
public class VideoSenderThread extends Thread {
    private static final long WAIT_TIME = 5000;
    private MediaCodec.BufferInfo eInfo;
    private long startTime = 0;
    private MediaCodec dstVideoEncoder;
    private final Object syncDstVideoEncoder = new Object();
    private RESFlvDataCollecter dataCollecter;
    private IRtspSender rtspSender;

    private boolean spsPpsSetted;

    VideoSenderThread(String name, MediaCodec encoder, RESFlvDataCollecter flvDataCollecter) {
        super(name);
        eInfo = new MediaCodec.BufferInfo();
        startTime = 0;
        dstVideoEncoder = encoder;
        dataCollecter = flvDataCollecter;
    }

    public void updateMediaCodec(MediaCodec encoder) {
        synchronized (syncDstVideoEncoder) {
            dstVideoEncoder = encoder;
        }
    }

    public void setVideoRtspSender(IRtspSender rtspSender) {
        this.rtspSender = rtspSender;
    }

    private boolean shouldQuit = false;

    void quit() {
        shouldQuit = true;
        this.interrupt();
    }

    @Override
    public synchronized void start() {
        spsPpsSetted = false;
        super.start();
    }

    @Override
    public void run() {
        while (!shouldQuit) {
            synchronized (syncDstVideoEncoder) {
                int eobIndex = MediaCodec.INFO_TRY_AGAIN_LATER;
                try {
                    eobIndex = dstVideoEncoder.dequeueOutputBuffer(eInfo, WAIT_TIME);
                } catch (Exception ignored) {
                }
                switch (eobIndex) {
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                        LogTools.d("VideoSenderThread,MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED");
                        break;
                    case MediaCodec.INFO_TRY_AGAIN_LATER:
//                        LogTools.d("VideoSenderThread,MediaCodec.INFO_TRY_AGAIN_LATER");
                        break;
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        LogTools.d("VideoSenderThread,MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:" +
                                dstVideoEncoder.getOutputFormat().toString());
                        sendRTSPAVCDecoderConfigurationRecord(dstVideoEncoder.getOutputFormat());
                        sendAVCDecoderConfigurationRecord(0, dstVideoEncoder.getOutputFormat());
                        break;
                    default:
                        LogTools.d("VideoSenderThread,MediaCode,eobIndex=" + eobIndex);
                        if (rtspSender != null) {//RTSP 推流
                            if ((eInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                if (!spsPpsSetted) {
                                    Pair<ByteBuffer, ByteBuffer> buffers =
                                            decodeSpsPpsFromBuffer(dstVideoEncoder.getOutputBuffers()[eobIndex],
                                                    eInfo.size);
                                    if (buffers != null) {
                                        rtspSender.onSPSandPPS(buffers.first, buffers.second);
                                        spsPpsSetted = true;
                                    }
                                }
                            } else {
                                //This ByteBuffer is H264
                                ByteBuffer bb = dstVideoEncoder.getOutputBuffers()[eobIndex];
                                rtspSender.onGetH264Data(bb, eInfo);
                            }
                        } else {//RTMP推流
                            if (startTime == 0) {
                                startTime = eInfo.presentationTimeUs / 1000;
                            }
                            /**
                             * we send sps pps already in INFO_OUTPUT_FORMAT_CHANGED
                             * so we ignore MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                             */
                            if (eInfo.flags != MediaCodec.BUFFER_FLAG_CODEC_CONFIG && eInfo.size != 0) {
                                ByteBuffer realData = dstVideoEncoder.getOutputBuffers()[eobIndex];
                                realData.position(eInfo.offset + 4);
                                realData.limit(eInfo.offset + eInfo.size);
                                sendRealData((eInfo.presentationTimeUs / 1000) - startTime, realData);
                            }
                        }
                        dstVideoEncoder.releaseOutputBuffer(eobIndex, false);
                        break;
                }
            }
            try {
                sleep(5);
            } catch (InterruptedException ignored) {
            }
        }
        eInfo = null;
    }

    /**
     * decode sps and pps if the encoder never call to MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
     */
    private Pair<ByteBuffer, ByteBuffer> decodeSpsPpsFromBuffer(ByteBuffer outputBuffer, int length) {
        byte[] mSPS = null, mPPS = null;
        byte[] csd = new byte[length];
        outputBuffer.get(csd, 0, length);
        int i = 0;
        int spsIndex = -1;
        int ppsIndex = -1;
        while (i < length - 4) {
            if (csd[i] == 0 && csd[i + 1] == 0 && csd[i + 2] == 0 && csd[i + 3] == 1) {
                if (spsIndex == -1) {
                    spsIndex = i;
                } else {
                    ppsIndex = i;
                    break;
                }
            }
            i++;
        }
        if (spsIndex != -1 && ppsIndex != -1) {
            mSPS = new byte[ppsIndex];
            System.arraycopy(csd, spsIndex, mSPS, 0, ppsIndex);
            mPPS = new byte[length - ppsIndex];
            System.arraycopy(csd, ppsIndex, mPPS, 0, length - ppsIndex);
        }
        if (mSPS != null && mPPS != null) {
            return new Pair<>(ByteBuffer.wrap(mSPS), ByteBuffer.wrap(mPPS));
        }
        return null;
    }
    private void sendRTSPAVCDecoderConfigurationRecord(MediaFormat mediaFormat) {
        if (rtspSender != null) {
            rtspSender.onSPSandPPS(mediaFormat.getByteBuffer("csd-0"),
                    mediaFormat.getByteBuffer("csd-1"));
            spsPpsSetted = true;
        }
    }

    private void sendAVCDecoderConfigurationRecord(long tms, MediaFormat format) {
        byte[] AVCDecoderConfigurationRecord = Packager.H264Packager.generateAVCDecoderConfigurationRecord(format);
        int packetLen = Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH +
                AVCDecoderConfigurationRecord.length;
        byte[] finalBuff = new byte[packetLen];
        Packager.FLVPackager.fillFlvVideoTag(finalBuff,
                0,
                true,
                true,
                AVCDecoderConfigurationRecord.length);
        System.arraycopy(AVCDecoderConfigurationRecord, 0,
                finalBuff, Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH, AVCDecoderConfigurationRecord.length);
        RESFlvData resFlvData = new RESFlvData();
        resFlvData.droppable = false;
        resFlvData.byteBuffer = finalBuff;
        resFlvData.size = finalBuff.length;
        resFlvData.dts = (int) tms;
        resFlvData.flvTagType = RESFlvData.FLV_RTMP_PACKET_TYPE_VIDEO;
        resFlvData.videoFrameType = RESFlvData.NALU_TYPE_IDR;
        dataCollecter.collect(resFlvData, RESRtmpSender.FROM_VIDEO);
    }

    private void sendRealData(long tms, ByteBuffer realData) {
        int realDataLength = realData.remaining();
        int packetLen = Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH +
                Packager.FLVPackager.NALU_HEADER_LENGTH +
                realDataLength;
        byte[] finalBuff = new byte[packetLen];
        realData.get(finalBuff, Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH +
                        Packager.FLVPackager.NALU_HEADER_LENGTH,
                realDataLength);
        int frameType = finalBuff[Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH +
                Packager.FLVPackager.NALU_HEADER_LENGTH] & 0x1F;
        Packager.FLVPackager.fillFlvVideoTag(finalBuff,
                0,
                false,
                frameType == 5,
                realDataLength);
        RESFlvData resFlvData = new RESFlvData();
        resFlvData.droppable = true;
        resFlvData.byteBuffer = finalBuff;
        resFlvData.size = finalBuff.length;
        resFlvData.dts = (int) tms;
        resFlvData.flvTagType = RESFlvData.FLV_RTMP_PACKET_TYPE_VIDEO;
        resFlvData.videoFrameType = frameType;
        dataCollecter.collect(resFlvData, RESRtmpSender.FROM_VIDEO);
    }
}