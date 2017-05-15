package com.pedro.rtsp.rtsp;

import android.text.TextUtils;
import android.util.Log;

import com.pedro.rtsp.rtp.sockets.ISocketSendListener;
import com.pedro.rtsp.utils.AuthUtil;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;
import com.pedro.rtsp.utils.CreateSSLSocket;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by pedro on 10/02/17.
 */

public class RtspClient implements ISocketSendListener {

    private final String TAG = "RtspClient";

    private final long mTimestamp;
    private String host;
    private int port;
    private String path;
    private int sampleRate;

    private final int trackVideo = 1;
    private final int trackAudio = 0;
    private Protocol protocol = Protocol.UDP;
    private int mCSeq = 0;
    private String authorization = null;
    private String user;
    private String password;
    private String sessionId;
    private ConnectCheckerRtsp connectCheckerRtsp;

    //sockets objects
    private Socket connectionSocket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private Thread thread;
    private String sps, pps;
    //for udp
    private int[] audioPorts = new int[]{5000, 5001};
    private int[] videoPorts = new int[]{5002, 5003};
    //for tcp
    private OutputStream outputStream;
    private volatile boolean streaming = false;
    //for secure transport
    private InputStream inputStreamJks = null;
    private String passPhraseJks = null;

    public RtspClient(ConnectCheckerRtsp connectCheckerRtsp, Protocol protocol) {
        this.protocol = protocol;
        this.connectCheckerRtsp = connectCheckerRtsp;
        long uptime = System.currentTimeMillis();
        mTimestamp = (uptime / 1000) << 32 & (((uptime - ((uptime / 1000) * 1000)) >> 32)
                / 1000); // NTP timestamp
    }

    public void setAuthorization(String user, String password) {
        this.user = user;
        this.password = password;
    }

    public void setJksData(InputStream inputStreamJks, String passPhraseJks) {
        this.inputStreamJks = inputStreamJks;
        this.passPhraseJks = passPhraseJks;
    }

    public boolean isStreaming() {
        return streaming;
    }

    public void setUrl(String url) {
        if (url.startsWith("rtsp://")) {
            try {
                String[] data = url.split("/");
                host = data[2].split(":")[0];
                port = Integer.parseInt(data[2].split(":")[1]);
                path = "";
                for (int i = 3; i < data.length; i++) {
                    path += "/" + data[i];
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                Log.e(TAG, "Error parse endPoint");
                e.printStackTrace();
                connectCheckerRtsp.onConnectionFailedRtsp();
                streaming = false;
            }
        } else {
            connectCheckerRtsp.onConnectionFailedRtsp();
        }
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getPath() {
        return path;
    }

    public void setSPSandPPS(String sps, String pps) {
        this.sps = sps;
        this.pps = pps;
    }

    public String getSps() {
        return sps;
    }

    public String getPps() {
        return pps;
    }

    public void connect() {
        if (!streaming) {
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (inputStreamJks == null | passPhraseJks == null) {
                            connectionSocket = new Socket(host, port);
                        } else {
                            connectionSocket = CreateSSLSocket.createSSlSocket(
                                    CreateSSLSocket.createKeyStore(inputStreamJks, passPhraseJks), host, port);
                        }
                        reader = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
                        outputStream = connectionSocket.getOutputStream();
                        writer = new BufferedWriter(new OutputStreamWriter(outputStream));
                        //test send
                        writer.write(sendOptions());
                        writer.flush();
                        getResponse(false);

                        writer.write(sendAnnounce());
                        writer.flush();
                        //check if you need credential for stream, if you need try connect with credential
                        String response = getResponse(false);
                        Log.e("TAG", "Announce response === " + response);
                        int status = getResponseStatus(response);
                        if (status == 403) {
                            connectCheckerRtsp.onConnectionFailedRtsp();
                            Log.e(TAG, "Response 403, access denied");
                            return;
                        } else if (status == 401) {
                            if (user == null || password == null) {
                                connectCheckerRtsp.onAuthErrorRtsp();
                                return;
                            } else {
                                writer.write(sendAnnounceWithAuth(response));
                                writer.flush();
                                if (getResponseStatus(getResponse(false)) == 401) {
                                    connectCheckerRtsp.onAuthErrorRtsp();
                                    return;
                                } else {
                                    connectCheckerRtsp.onAuthSuccessRtsp();
                                }
                            }
                        }
                        String audioSetup = sendSetup(trackAudio, protocol);
                        Log.e("TAG", "audioSetup == " + audioSetup);
                        writer.write(audioSetup);
                        writer.flush();
                        getResponse(true);
                        String videoSetUp = sendSetup(trackVideo, protocol);
                        Log.e("TAG", "videoSetUp == " + videoSetUp);
                        writer.write(videoSetUp);
                        writer.flush();
                        getResponse(false);
                        writer.write(sendRecord());
                        writer.flush();
                        getResponse(false);

                        streaming = true;
                        Log.e("TAG", "11111111111111111111111111");
                        connectCheckerRtsp.onConnectionSuccessRtsp();
                        new Thread(connectionMonitor).start();
                    } catch (IOException e) {
                        e.printStackTrace();
                        connectCheckerRtsp.onConnectionFailedRtsp();
                        streaming = false;
                    }
                }
            });
            thread.start();
        }
    }

    private Runnable connectionMonitor = new Runnable() {
        @Override
        public void run() {
            if (streaming) {
                try {
                    // We poll the RTSP server with OPTION requests
                    writer.write(sendOptions());
                    writer.flush();
                    getResponse(false);
                    Thread.sleep(6000);
                    new Thread(connectionMonitor).start();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                    connectCheckerRtsp.onConnectionFailedRtsp();
                    streaming = false;
                }
            }
        }
    };

    public void disconnect() {
        if (streaming) {
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        writer.write(sendTearDown());
                        connectionSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    connectCheckerRtsp.onDisconnectRtsp();
                    streaming = false;
                }
            });
            thread.start();
        }
    }

    private String sendAnnounce() {
        String body = createBody();
        String request;
        if (authorization == null) {
            request = "ANNOUNCE rtsp://" + host + ":" + port + path + " RTSP/1.0\r\n" +
                    "CSeq: " + (++mCSeq) + "\r\n" +
                    "Content-Length: " + body.length() + "\r\n" +
                    "Content-Type: application/sdp\r\n\r\n" +
                    body;
        } else {
            request = "ANNOUNCE rtsp://" + host + ":" + port + path + " RTSP/1.0\r\n" +
                    "CSeq: " + (++mCSeq) + "\r\n" +
                    "Content-Length: " + body.length() + "\r\n" +
                    "Authorization: " + authorization + "\r\n" +
                    "Content-Type: application/sdp\r\n\r\n" +
                    body;
        }
        Log.e("TAG", "Announce request == " + request);
        return request;
    }

    private String createBody() {
        return "v=0\r\n" +
                // TODO: Add IPV6 support
                "o=- " + mTimestamp + " " + mTimestamp + " IN IP4 " + "127.0.0.1" + "\r\n" +
                "s=Unnamed\r\n" +
                "i=N/A\r\n" +
                "c=IN IP4 " + host + "\r\n" +
                // thread=0 0 means the session is permanent (we don'thread know when it will stop)
                "t=0 0\r\n" +
                "a=recvonly\r\n" +
                Body.createAudioBody(trackAudio, sampleRate) +
                Body.createVideoBody(trackVideo, sps, pps);
    }

    private String sendSetup(int track, Protocol protocol) {
        String params =
                (protocol == Protocol.UDP) ?
                        ("UDP;unicast;client_port=" + (5000 + 2 * track) + "-" + (5000
                                + 2 * track
                                + 1) + ";mode=receive") :
                        ("TCP;interleaved=" + 2 * track + "-" + (2 * track + 1) + ";mode=record");
        String setupStr = "SETUP rtsp://" + host + ":" + port + path + "/streamid=" + track + " RTSP/1.0\r\n" +
                "Transport: RTP/AVP/" + params + "\r\n" +
                addHeaders(authorization);
        Log.e("TAG", "setupStr == " + setupStr);
        return setupStr;
    }

    private String sendOptions() {
        return "OPTIONS rtsp://" + host + ":" + port + path + " RTSP/1.0\r\n" +
                addHeaders(authorization);
    }

    private String sendRecord() {
        return "RECORD rtsp://" + host + ":" + port + path + " RTSP/1.0\r\n" +
                "Range: npt=0.000-\r\n" +
                addHeaders(authorization);
    }

    private String sendTearDown() {
        return "TEARDOWN rtsp://" + host + ":" + port + path + " RTSP/1.0\r\n" +
                addHeaders(authorization);
    }

    private String addHeaders(String authorization) {
        String sessionStr = !TextUtils.isEmpty(sessionId) ? "Session: "
                + sessionId
                + "\r\n" :
                "";
        return "CSeq: "
                + (++mCSeq)
                + "\r\n"
                +
                "Content-Length: 0\r\n"
                +
                sessionStr
                +
                // For some reason you may have to remove last "\r\n" in the next line to make the RTSP client work with your wowza server :/
                (authorization != null ? "Authorization: " + authorization + "\r\n" : "")
                + "\r\n";
    }

    private String getResponse(boolean isAudio) {
        try {
            String response = "";
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Session")) {
                    sessionId = line.split(";")[0].split(":")[1].trim();
                }
                if (line.contains("server_port")) {
                    String[] s = line.split("server_port=")[1].split("-");
                    for (int i = 0; i < s.length; i++) {
                        if (isAudio) {
                            audioPorts[i] = Integer.parseInt(s[i]);
                        } else {
                            Log.e("Ports", s[i]);
                            videoPorts[i] = Integer.parseInt(s[i]);
                        }
                    }
                }
                response += line + "\n";
                //end of response
                if (line.length() < 3) break;
            }
            Log.e("TAG", "sessionId  == " + sessionId);
            Log.e("TAG", "RTSP res ==== " + response);
            return response;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private String sendAnnounceWithAuth(String authResponse) {
        authorization = createAuth(authResponse);
        Log.e("Auth", authorization);
        String body = createBody();
        String request = "ANNOUNCE rtsp://" + host + ":" + port + path + " RTSP/1.0\r\n" +
                "CSeq: " + (++mCSeq) + "\r\n" +
                "Content-Length: " + body.length() + "\r\n" +
                "Authorization: " + authorization + "\r\n" +
                "Content-Type: application/sdp\r\n\r\n" +
                body;
        return request;
    }

    private String createAuth(String authResponse) {
        Pattern authPattern =
                Pattern.compile("realm=\"(.+)\",\\s+nonce=\"(\\w+)\"", Pattern.CASE_INSENSITIVE);
        Matcher matcher = authPattern.matcher(authResponse);
        matcher.find();
        String realm = matcher.group(1);
        String nonce = matcher.group(2);
        String hash1 = AuthUtil.getMd5Hash(user + ":" + realm + ":" + password);
        String hash2 = AuthUtil.getMd5Hash("ANNOUNCE:rtsp://" + host + ":" + port + path);
        String hash3 = AuthUtil.getMd5Hash(hash1 + ":" + nonce + ":" + hash2);
        return "Digest username=\""
                + user
                + "\",realm=\""
                + realm
                + "\",nonce=\""
                + nonce
                + "\",uri=\"rtsp://"
                + host
                + ":"
                + port
                + path
                + "\",response=\""
                + hash3
                + "\"";
    }

    private int getResponseStatus(String response) {
        Matcher matcher =
                Pattern.compile("RTSP/\\d.\\d (\\d+) (\\w+)", Pattern.CASE_INSENSITIVE).matcher(response);
        matcher.find();
        return Integer.parseInt(matcher.group(1));
    }

    public int[] getAudioPorts() {
        return audioPorts;
    }

    public int[] getVideoPorts() {
        return videoPorts;
    }

    @Override
    public void onSendError() {
        if (connectCheckerRtsp != null) {
            connectCheckerRtsp.onWriteError();
        }
    }
}

