package me.lake.librestreaming.client;


import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.os.Build;

import me.lake.librestreaming.core.listener.RESConnectionListener;
import me.lake.librestreaming.core.listener.RESScreenShotListener;
import me.lake.librestreaming.core.listener.RESVideoChangeListener;
import me.lake.librestreaming.filter.hardvideofilter.BaseHardVideoFilter;
import me.lake.librestreaming.filter.softaudiofilter.BaseSoftAudioFilter;
import me.lake.librestreaming.filter.softvideofilter.BaseSoftVideoFilter;
import me.lake.librestreaming.model.RESConfig;
import me.lake.librestreaming.model.RESCoreParameters;
import me.lake.librestreaming.model.Size;
import me.lake.librestreaming.rtmp.RESFlvData;
import me.lake.librestreaming.rtmp.RESFlvDataCollecter;
import me.lake.librestreaming.rtmp.RESRtmpSender;
import me.lake.librestreaming.rtsp.IRtspSender;
import me.lake.librestreaming.tools.LogTools;

/**
 * Created by lake on 16-3-16.
 */
public class RESClient {
    private RESVideoClient videoClient;
    private RESAudioClient audioClient;
    private final Object SyncOp;
    //parameters
    RESCoreParameters coreParameters;
    private ISender rtmpSender;
    private RESFlvDataCollecter dataCollecter;
    private IRtspSender rtspSender;

    public RESClient() {
        SyncOp = new Object();
        coreParameters = new RESCoreParameters();
        CallbackDelivery.i();
    }

    /**
     * prepare to stream
     *
     * @param resConfig config
     * @return true if prepare success
     */
    public boolean prepare(RESConfig resConfig) {
        synchronized (SyncOp) {
            checkDirection(resConfig);
            coreParameters.filterMode = resConfig.getFilterMode();
            coreParameters.rtmpAddr = resConfig.getRtmpAddr();
            coreParameters.printDetailMsg = resConfig.isPrintDetailMsg();
            coreParameters.senderQueueLength = 225;
            videoClient = new RESVideoClient(coreParameters);
            audioClient = new RESAudioClient(coreParameters);
            audioClient.setAudioRtspSender(rtspSender);
            videoClient.setVideoRtspSender(rtspSender);
            if (!videoClient.prepare(resConfig)) {
                LogTools.d("!!!!!videoClient.prepare()failed");
                LogTools.d(coreParameters.toString());
                return false;
            }
            if (!audioClient.prepare(resConfig)) {
                LogTools.d("!!!!!audioClient.prepare()failed");
                LogTools.d(coreParameters.toString());
                return false;
            }
            if (resConfig.isUserRTSPPusher() && rtspSender != null) {
                rtmpSender = rtspSender;
            } else {
                rtmpSender = new RESRtmpSender();
                //            rtmpSender = new QuenRtmpSender();
            }
            rtmpSender.prepare(coreParameters);
            dataCollecter = new RESFlvDataCollecter() {
                @Override
                public void collect(RESFlvData flvData, int type) {
                    if (rtmpSender != null && rtmpSender instanceof RESRtmpSender) {
                        ((RESRtmpSender) rtmpSender).feed(flvData, type);
                    }
                }
            };
            coreParameters.done = true;
            LogTools.d("===INFO===coreParametersReady:");
            LogTools.d(coreParameters.toString());
            return true;
        }
    }

    /**
     * 在开始直播之前有效
     *
     * @param url
     */
    public void setRtmpAddress(String url) {
        coreParameters.rtmpAddr = url;
    }

    /**
     * start streaming
     */
    public void startStreaming() {
        synchronized (SyncOp) {
            videoClient.startStreaming(dataCollecter);
            rtmpSender.start(coreParameters.rtmpAddr);
            audioClient.start(dataCollecter);
            LogTools.d("RESClient,startStreaming()");
        }
    }

    /**
     * stop streaming
     */
    public void stopStreaming() {
        synchronized (SyncOp) {
            videoClient.stopStreaming();
            audioClient.stop();
            rtmpSender.stop();
            LogTools.d("RESClient,stopStreaming()");
        }
    }

    /**
     * clean up
     */
    public void destroy() {
        synchronized (SyncOp) {
            rtmpSender.destroy();
            videoClient.destroy();
            audioClient.destroy();
            rtmpSender = null;
            videoClient = null;
            audioClient = null;
            LogTools.d("RESClient,destroy()");
        }
    }

    /**
     * call it AFTER {@link #prepare(RESConfig)}
     *
     * @param surfaceTexture to rendering preview
     */
    public void startPreview(SurfaceTexture surfaceTexture, int visualWidth, int visualHeight) {
        videoClient.startPreview(surfaceTexture, visualWidth, visualHeight);
        LogTools.d("RESClient,startPreview()");
    }

    public void updatePreview(int visualWidth, int visualHeight) {
        videoClient.updatePreview(visualWidth, visualHeight);
        LogTools.d("RESClient,updatePreview()");
    }

    /**
     * @param releaseTexture true if you won`t reuse this surfaceTexture later
     */
    public void stopPreview(boolean releaseTexture) {
        if (videoClient != null) {
            videoClient.stopPreview(releaseTexture);
        }
        LogTools.d("RESClient,stopPreview()");
    }

    /**
     * change camera on running.<br/>
     */
    public boolean swapCamera() {
        synchronized (SyncOp) {
            LogTools.d("RESClient,swapCamera()");
            return videoClient.swapCamera();
        }
    }

    /**
     * only for soft filter mode.<br/>
     * use it to update filter property.<br/>
     * call it with {@link #releaseSoftVideoFilter()}<br/>
     * make sure to release it in 3ms
     *
     * @return the videofilter in use
     */
    public BaseSoftVideoFilter acquireSoftVideoFilter() {
        return videoClient.acquireSoftVideoFilter();
    }

    /**
     * only for soft filter mode.<br/>
     * call it with {@link #acquireSoftVideoFilter()}
     */
    public void releaseSoftVideoFilter() {
        videoClient.releaseSoftVideoFilter();
    }

    /**
     * get the real video size,call after prepare()
     *
     * @return
     */
    public Size getVideoSize() {
        return new Size(coreParameters.videoWidth, coreParameters.videoHeight);
    }

    /**
     * get the rtmp server ip addr ,call after connect success.
     *
     * @return
     */
    public String getServerIpAddr() {
        synchronized (SyncOp) {
            return rtmpSender == null || !(rtmpSender instanceof RESRtmpSender)
                    ? null : ((RESRtmpSender) rtmpSender).getServerIpAddr();
        }
    }

    /**
     * get the real draw frame rate of screen
     *
     * @return
     */
    public float getDrawFrameRate() {
        synchronized (SyncOp) {
            return videoClient == null ? 0 : videoClient.getDrawFrameRate();
        }
    }

    /**
     * get the rate of video frame sent by rtmp
     *
     * @return
     */
    public float getSendFrameRate() {
        synchronized (SyncOp) {
            return rtmpSender == null || !(rtmpSender instanceof RESRtmpSender)
                    ? 0 : ((RESRtmpSender) rtmpSender).getSendFrameRate();
        }
    }

    /**
     * get free percent of send buffer
     * return ~0.0 if the netspeed is not enough or net is blocked.
     *
     * @return
     */
    public float getSendBufferFreePercent() {
        synchronized (SyncOp) {
            return rtmpSender == null || !(rtmpSender instanceof RESRtmpSender)
                    ? 0 : ((RESRtmpSender) rtmpSender).getSendBufferFreePercent();
        }
    }

    /**
     * only for soft filter mode.<br/>
     * set videofilter.<br/>
     * can be called Repeatedly.<br/>
     * do NOT call it between {@link #acquireSoftVideoFilter()} & {@link #releaseSoftVideoFilter()}
     *
     * @param baseSoftVideoFilter videofilter to apply
     */
    public void setSoftVideoFilter(BaseSoftVideoFilter baseSoftVideoFilter) {
        videoClient.setSoftVideoFilter(baseSoftVideoFilter);
    }

    /**
     * only for hard filter mode.<br/>
     * use it to update filter property.<br/>
     * call it with {@link #releaseHardVideoFilter()}<br/>
     * make sure to release it in 3ms
     *
     * @return the videofilter in use
     */
    public BaseHardVideoFilter acquireHardVideoFilter() {
        return videoClient.acquireHardVideoFilter();
    }

    /**
     * only for hard filter mode.<br/>
     * call it with {@link #acquireHardVideoFilter()}
     */
    public void releaseHardVideoFilter() {
        videoClient.releaseHardVideoFilter();
    }

    /**
     * only for hard filter mode.<br/>
     * set videofilter.<br/>
     * can be called Repeatedly.<br/>
     * do NOT call it between {@link #acquireHardVideoFilter()} & {@link #acquireHardVideoFilter()}
     *
     * @param baseHardVideoFilter videofilter to apply
     */
    public void setHardVideoFilter(BaseHardVideoFilter baseHardVideoFilter) {
        videoClient.setHardVideoFilter(baseHardVideoFilter);
    }

    /**
     * set audiofilter.<br/>
     * can be called Repeatedly.<br/>
     * do NOT call it between {@link #acquireSoftAudioFilter()} & {@link #releaseSoftAudioFilter()}
     *
     * @param baseSoftAudioFilter audiofilter to apply
     */
    public void setSoftAudioFilter(BaseSoftAudioFilter baseSoftAudioFilter) {
        audioClient.setSoftAudioFilter(baseSoftAudioFilter);
    }

    /**
     * use it to update filter property.<br/>
     * call it with {@link #releaseSoftAudioFilter()}<br/>
     * make sure to release it in 3ms
     *
     * @return the audiofilter in use
     */
    public BaseSoftAudioFilter acquireSoftAudioFilter() {
        return audioClient.acquireSoftAudioFilter();
    }

    /**
     * call it with {@link #acquireSoftAudioFilter()}
     */
    public void releaseSoftAudioFilter() {
        audioClient.releaseSoftAudioFilter();
    }

    /**
     * get video & audio real send Speed
     *
     * @return speed in B/s
     */
    public int getAVSpeed() {
        synchronized (SyncOp) {
            return rtmpSender == null || !(rtmpSender instanceof RESRtmpSender)
                    ? 0 : ((RESRtmpSender) rtmpSender).getTotalSpeed();
        }
    }

    /**
     * call it AFTER {@link #prepare(RESConfig)}
     *
     * @param connectionListener
     */
    public void setConnectionListener(RESConnectionListener connectionListener) {
        rtmpSender.setConnectionListener(connectionListener);
    }

    /**
     * listener for video size change
     *
     * @param videoChangeListener
     */
    public void setVideoChangeListener(RESVideoChangeListener videoChangeListener) {
        videoClient.setVideoChangeListener(videoChangeListener);
    }

    /**
     * get the param of video,audio,mediacodec
     *
     * @return info
     */
    public String getConfigInfo() {
        return coreParameters.toString();
    }

    /**
     * set zoom by percent [0.0f,1.0f]
     *
     * @param targetPercent zoompercent
     */
    public boolean setZoomByPercent(float targetPercent) {
        return videoClient.setZoomByPercent(targetPercent);
    }

    /**
     * 必须在prepare之前调用,有效。
     * 在使用RTSP推流的时候一定要使用
     *
     * @param rtspSender
     */
    public void setRtspSender(IRtspSender rtspSender) {
        this.rtspSender = rtspSender;
    }

    /**
     * toggle flash light
     *
     * @return true if operation success
     */
    public boolean toggleFlashLight() {
        return videoClient.toggleFlashLight();
    }

    public void takeScreenShot(RESScreenShotListener listener) {
        videoClient.takeScreenShot(listener);
    }

    /**
     * Change video bitrate on the fly<br/>
     * call between {@link #startStreaming()} & {@link #stopStreaming()}
     *
     * @param bitrate target bitrate bits/sec
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public void reSetVideoBitrate(int bitrate) {
        videoClient.reSetVideoBitrate(bitrate);
    }

    /**
     * get current bitrate
     *
     * @return current bitrate bits/sec
     */
    public int getVideoBitrate() {
        return videoClient.getVideoBitrate();
    }

    /**
     * update fps on the fly.
     *
     * @param fps
     */
    public void reSetVideoFPS(int fps) {
        videoClient.reSetVideoFPS(fps);
    }

    /**
     * only work with hard mode.
     * reset video size on the fly.
     * may restart camera.
     * will restart mediacodec.
     * will not interrupt streaming
     *
     * @param targetVideoSize
     */
    public void reSetVideoSize(Size targetVideoSize) {
        if (targetVideoSize == null) {
            return;
        }
        if (coreParameters.filterMode == RESCoreParameters.FILTER_MODE_SOFT) {
            throw new IllegalArgumentException("soft mode doesn`t support reSetVideoSize");
        } else {
            videoClient.reSetVideoSize(targetVideoSize);
        }
    }

    public String getVertion() {
        return Constants.VERSION;
    }

    /**
     * =====================PRIVATE=================
     **/
    private void checkDirection(RESConfig resConfig) {
        int frontFlag = resConfig.getFrontCameraDirectionMode();
        int backFlag = resConfig.getBackCameraDirectionMode();
        int fbit = 0;
        int bbit = 0;
        if ((frontFlag >> 4) == 0) {
            frontFlag |= RESCoreParameters.FLAG_DIRECTION_ROATATION_0;
        }
        if ((backFlag >> 4) == 0) {
            backFlag |= RESCoreParameters.FLAG_DIRECTION_ROATATION_0;
        }
        for (int i = 4; i <= 8; ++i) {
            if (((frontFlag >> i) & 0x1) == 1) {
                fbit++;
            }
            if (((backFlag >> i) & 0x1) == 1) {
                bbit++;
            }
        }
        if (fbit != 1 || bbit != 1) {
            throw new RuntimeException("invalid direction rotation flag:frontFlagNum=" + fbit + ",backFlagNum=" + bbit);
        }
        if (((frontFlag & RESCoreParameters.FLAG_DIRECTION_ROATATION_0) != 0) || ((frontFlag & RESCoreParameters.FLAG_DIRECTION_ROATATION_180) != 0)) {
            fbit = 0;
        } else {
            fbit = 1;
        }
        if (((backFlag & RESCoreParameters.FLAG_DIRECTION_ROATATION_0) != 0) || ((backFlag & RESCoreParameters.FLAG_DIRECTION_ROATATION_180) != 0)) {
            bbit = 0;
        } else {
            bbit = 1;
        }
        if (bbit != fbit) {
            if (bbit == 0) {
                throw new RuntimeException("invalid direction rotation flag:back camera is landscape but front camera is portrait");
            } else {
                throw new RuntimeException("invalid direction rotation flag:back camera is portrait but front camera is landscape");
            }
        }
        if (fbit == 1) {
            coreParameters.isPortrait = true;
        } else {
            coreParameters.isPortrait = false;
        }
        coreParameters.backCameraDirectionMode = backFlag;
        coreParameters.frontCameraDirectionMode = frontFlag;
    }

    static {
        System.loadLibrary("restreaming");
    }
}
