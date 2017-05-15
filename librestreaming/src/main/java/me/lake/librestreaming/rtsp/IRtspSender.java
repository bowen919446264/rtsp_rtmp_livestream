package me.lake.librestreaming.rtsp;

import android.media.MediaCodec;

import java.nio.ByteBuffer;

import me.lake.librestreaming.client.ISender;
import me.lake.librestreaming.core.listener.RESConnectionListener;
import me.lake.librestreaming.model.RESCoreParameters;

/**
 * Created by liuwb on 2017/4/26.
 */
public interface IRtspSender extends ISender {
    /**
     * rtsp 的准备工作
     *
     * @param coreParameters
     */
    void prepare(RESCoreParameters coreParameters);

    void onSetAudioSampleRate(int sampleRate);

    /**
     * 设置统一的连接回调
     *
     * @param connectionListener
     */
    void setConnectionListener(RESConnectionListener connectionListener);

    /**
     * 得到视频的sps pps 信息
     *
     * @param sps
     * @param pps
     */
    void onSPSandPPS(ByteBuffer sps, ByteBuffer pps);

    /**
     * 编码之后的图像真实数据
     *
     * @param h264Buffer
     * @param info
     */
    void onGetH264Data(ByteBuffer h264Buffer, MediaCodec.BufferInfo info);

    /**
     * 编码之后的音频真实数据
     *
     * @param accBuffer
     * @param info
     */
    void onGetAccData(ByteBuffer accBuffer, MediaCodec.BufferInfo info);

    /**
     * 开始设置RTSP 服务器信息
     *
     * @param rtspAddr
     */
    void start(String rtspAddr);

    /**
     * 停止推流
     */
    void stop();

}
