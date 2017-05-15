package com.pedro.rtsp.rtsp;

import android.util.Log;

import com.pedro.rtsp.utils.RtpConstants;

/**
 * Created by pedro on 21/02/17.
 */

public class Body {

    /**
     * supported sampleRates.
     **/
    private static final int[] AUDIO_SAMPLING_RATES = {
            96000, // 0
            88200, // 1
            64000, // 2
            48000, // 3
            44100, // 4
            32000, // 5
            24000, // 6
            22050, // 7
            16000, // 8
            12000, // 9
            11025, // 10
            8000,  // 11
            7350,  // 12
            -1,   // 13
            -1,   // 14
            -1,   // 15
    };

    public static String createAudioBody(int trackAudio, int sampleRate) {
        Log.e("TAG", "sampleRate == " + sampleRate);
        int sampleRateNum = -1;
        for (int i = 0; i < AUDIO_SAMPLING_RATES.length; i++) {
            if (AUDIO_SAMPLING_RATES[i] == sampleRate) {
                sampleRateNum = i;
                break;
            }
        }
        int config = (2 & 0x1F) << 11 | (sampleRateNum & 0x0F) << 7 | (1 & 0x0F) << 3;
        return "m=audio " + (5000 + 2 * trackAudio) + " RTP/AVP " + RtpConstants.playLoadType + "\r\n" +
                "a=rtpmap:" + RtpConstants.playLoadType + " mpeg4-generic/" + sampleRate + "\r\n" +
                "a=fmtp:" + RtpConstants.playLoadType + " streamtype=5; profile-level-id=15; mode=AAC-hbr; config=" +
                Integer.toHexString(config) + "; SizeLength=13; IndexLength=3; IndexDeltaLength=3;\r\n"
                + "a=control:streamid=" + trackAudio + "\r\n";
    }

    public static String createVideoBody(int trackVideo, String sps, String pps) {
        return "m=video " + (5000 + 2 * trackVideo) + " RTP/AVP " + RtpConstants.playLoadType + "\r\n" +
                "a=rtpmap:" + RtpConstants.playLoadType + " H264/" + RtpConstants.clockVideoFrequency + "\r\n" +
                "a=fmtp:" + RtpConstants.playLoadType + " packetization-mode=1;profile-level-id=" + "42c029" + ";sprop-parameter-sets=" + sps + "," + pps + ";\r\n"
                + "a=control:streamid=" + trackVideo + "\r\n";
    }
}
