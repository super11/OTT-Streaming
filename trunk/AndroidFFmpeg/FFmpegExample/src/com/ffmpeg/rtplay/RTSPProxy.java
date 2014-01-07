package com.ffmpeg.rtplay;


import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.StringTokenizer;

// http://www.csee.umbc.edu/~pmundur/courses/CMSC691C/lab5-kurose-ross.html

public class RTSPProxy implements Runnable {
    // RTP variables:
    // ----------------

    DatagramSocket rtpDestSocket; // socket to be used to send and receive UDP

    // packets

    WifiManager.MulticastLock multicastLock;

    MulticastSocket rtpSourceSocket;

    byte[] recvBuffer = new byte[65535];

    DatagramPacket senddp; // UDP packet containing the video frames

    InetAddress ClientIPAddr; // Client IP address

    int RTSP_listen_port = 5004;

    InetAddress RTP_source_addr;

    int RTP_source_port;

    int RTP_dest_port[] = {
            0, 0
    }; // destination port for RTP packets (given by the

    // RTSP Client)

    // Video variables:
    // ----------------
    static int MJPEG_TYPE = 26; // RTP payload type for MJPEG video

    boolean sendStream = false;

    // RTSP variables
    // ----------------
    // rtsp states
    final static int INIT = 0;

    final static int READY = 1;

    final static int PLAYING = 2;

    // rtsp message types
    final static int DESCRIBE = 3;

    final static int SETUP = 4;

    final static int PLAY = 5;

    final static int PAUSE = 6;

    final static int TEARDOWN = 7;

    final static int OPTIONS = 8;

    static int state; // RTSP Server state == INIT or READY or PLAY

    Socket RTSPsocket; // socket used to send/receive RTSP messages

    String RTSPProfile;

    // input and output stream filters
    static BufferedReader RTSPBufferedReader;

    static BufferedWriter RTSPBufferedWriter;

    static String VideoFileName; // video file requested from the client

    static int RTSP_ID = 123456; // ID of the RTSP session

    int RTSPSeqNb = 0; // Sequence number of RTSP messages within the session

    Thread workerThread;

    public final static class RtspConstants {

        // rtsp states
        public static int INIT = 0;

        public static int READY = 1;

        public static int PLAYING = 2;

        public static int UNDEFINED = 3;

        // rtsp message types
        public static int OPTIONS = 3;

        public static int DESCRIBE = 4;

        public static int SETUP = 5;

        public static int PLAY = 6;

        public static int PAUSE = 7;

        public static int TEARDOWN = 8;

        public static String SDP_AUDIO_TYPE = "audio";

        public static String SDP_VIDEO_TYPE = "video";

        // the payload type is part of the SDP description
        // sent back as an answer to a DESCRIBE request.

        // android actually supports video streaming from
        // the camera using H.263-1998

        // TODO: sync with
        // com.orangelabs.rcs.core.ims.protocol.rtp.format.video.H263VideoFormat.PAYLOAD
        // = 97
        // com.orangelabs.rcs.core.ims.protocol.rtp.format.video.H264VideoFormat.PAYLOAD
        // = 96
        public static int RTP_H264_PAYLOADTYPE = 96; // dynamic range

        public static int RTP_H263_PAYLOADTYPE = 97; // dynamic range

        public static String H263_1998 = "H263-1998/90000";

        public static String H263_2000 = "H263-2000/90000";

        public static String H264 = "H264/90000";

        // TODO: synchronize settings
        // com.orangelabs.rcs.core.ims.protocol.rtp.codec.video.h263.H263Config
        // com.orangelabs.rcs.core.ims.protocol.rtp.codec.video.h264.H264Config

        // QCIF
        // public static String WIDTH = "176";
        // public static String HEIGHT = "144";

        // QCIF
        public static String WIDTH = "720";

        public static String HEIGHT = "480";

        public static final int FPS = 60;

        // public static final int BITRATE = 128000; // h263-2000

        public static final int BITRATE = 64000; // for h264

        public static final String CRLF = "\r\n";

        public static final String CRLF2 = "\r\n\r\n";

        public static final String SEP = " ";

        // default client ports for audio and video streaming;
        // the port is usually provided with an RTSP request
        public static final int CLIENT_AUDIO_PORT = 2000;

        public static final int CLIENT_VIDEO_PORT = 4000;

        // public static String SERVER_IP = "spexhd2:8080";
        public static int SERVER_PORT = 8080;

        public static String SERVER_IP = getLocalIpAddress() + ":" + SERVER_PORT;

        public static String SERVER_NAME = "KuP RTSP Server";

        public static String SERVER_VERSION = "0.1";

        public static int PORT_BASE = 3000;

        public static int[] PORTS_RTSP_RTP = {
                PORT_BASE, (PORT_BASE + 1)
        };

        public static final String DIR_MULTIMEDIA = "../";

        // tags for logging
        public static String SERVER_TAG = "RtspServer";

        public static String getLocalIpAddress() {
            // http://www.droidnova.com/get-the-ip-address-of-your-device,304.html
            try {
                for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en
                        .hasMoreElements();) {
                    NetworkInterface intf = en.nextElement();
                    for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr
                            .hasMoreElements();) {
                        InetAddress inetAddress = enumIpAddr.nextElement();
                        if (!inetAddress.isLoopbackAddress()) {
                            return inetAddress.getHostAddress().toString();
                        }
                    }
                }
            } catch (SocketException ex) {
                Log.e("RtspConstants", ex.toString());
            }
            return null;
        }
    }

    // --------------------------------
    // Constructor
    // --------------------------------
    public RTSPProxy() {
    }

    // ------------------------------------
    // main
    // ------------------------------------
    public void start(Context ctx, final int Profile, final int RTSPport,
            final InetAddress sourceAddr, final int sourcePort) throws Exception {

        RTSPProfile = String.format(ctx.getResources().getString(Profile), RTSPport);

        // Allow WiFi to recieve Multicast data not sent to itself
        WifiManager wm = (WifiManager)ctx.getSystemService(Context.WIFI_SERVICE);
        multicastLock = wm.createMulticastLock("mydebuginfo");

        RTSP_listen_port = RTSPport;
        RTP_source_addr = sourceAddr;
        RTP_source_port = sourcePort;

        workerThread = new Thread(this);
        workerThread.start();

        // Wait for the process to start listening
        synchronized (this) {
            RTSPProxy.this.wait();
        }
    }

    @Override
    public void run() {
        // Initiate TCP connection with the client for the RTSP session
        ServerSocket listenSocket;
        try {
            listenSocket = new ServerSocket(RTSP_listen_port);

            // Resume the thread that is waiting for the process to start
            // listening
            synchronized (this) {
                RTSPProxy.this.notifyAll();
            }

            RTSPsocket = listenSocket.accept();
            listenSocket.close();

            // Get Client IP address
            ClientIPAddr = RTSPsocket.getInetAddress();

            // Initiate RTSPstate
            state = INIT;

            // Set input and output stream filters:
            RTSPBufferedReader = new BufferedReader(new InputStreamReader(
                    this.RTSPsocket.getInputStream()));
            RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(
                    this.RTSPsocket.getOutputStream()));

        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        try {
            // loop to handle RTSP requests
            while (true) {
            	Thread.yield();
                if (RTSPBufferedReader.ready()) {
                    // parse the request
                    int request_type = parse_RTSP_request(); // blocking

                    switch (request_type) {
                        case DESCRIBE:
                            send_RTSP_describe_response();
                            break;

                        case SETUP:
                            // update RTSP state
                            state = READY;
                            System.out.println("New RTSP state: READY");

                            // Send response
                            send_RTSP_setup_response();

                            // init RTP socket
                            multicastLock.acquire();

                            rtpDestSocket = new DatagramSocket();
                            rtpSourceSocket = new MulticastSocket();
                            rtpSourceSocket.joinGroup(RTP_source_addr);
                            rtpSourceSocket.setSoTimeout(100);
                            break;

                        case PLAY:
                            if (state == READY) {
                                // send back response
                                send_RTSP_response();
                                // start the stream
                                sendStream = true;
                                // update state
                                state = PLAYING;
                                System.out.println("New RTSP state: PLAYING");
                            }
                            break;

                        case PAUSE:
                            if (state == PLAYING) {
                                // send back response
                                send_RTSP_response();
                                // stop the stream
                                sendStream = false;
                                // update state
                                state = READY;
                                System.out.println("New RTSP state: READY");
                            }
                            break;

                        case TEARDOWN:
                            // send back response
                            send_RTSP_response();
                            // stop the stream
                            sendStream = false;
                            // close sockets
                            RTSPsocket.close();
                            rtpDestSocket.close();

                            multicastLock.release();
                            break;

                        case OPTIONS:
                            send_RTSP_options_response();
                            break;
                    }
                }

                if (state == PLAYING) {
                    handleVideoStream();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ------------------------
    // Handler for timer
    // ------------------------
    private void handleVideoStream() {
        try {

            DatagramPacket packet = new DatagramPacket(recvBuffer, recvBuffer.length,
                    RTP_source_addr, RTP_source_port);
            rtpSourceSocket.receive(packet);

            if (packet.getLength() > 0) {
                byte[] data = packet.getData();

                // send the packet as a DatagramPacket over the UDP socket
                senddp = new DatagramPacket(data, data.length, ClientIPAddr, RTP_dest_port[0]);
                rtpDestSocket.send(senddp);
            }
        } catch (Exception ex) {
            System.out.println("Exception caught: " + ex);
            System.exit(0);
        }
    }

    // ------------------------------------
    // Parse RTSP Request
    // ------------------------------------
    private int parse_RTSP_request() {
        int request_type = -1;
        try {
            // parse request line and extract the request_type:
            String RequestLine = RTSPBufferedReader.readLine();

            if (RequestLine != null) {
                // System.out.println("RTSP Server - Received from Client:");
                System.out.println(RequestLine);

                StringTokenizer tokens = new StringTokenizer(RequestLine);
                String request_type_string = tokens.nextToken();

                // convert to request_type structure:
                if ((new String(request_type_string)).compareTo("DESCRIBE") == 0)
                    request_type = DESCRIBE;
                else if ((new String(request_type_string)).compareTo("SETUP") == 0)
                    request_type = SETUP;
                else if ((new String(request_type_string)).compareTo("PLAY") == 0)
                    request_type = PLAY;
                else if ((new String(request_type_string)).compareTo("PAUSE") == 0)
                    request_type = PAUSE;
                else if ((new String(request_type_string)).compareTo("TEARDOWN") == 0)
                    request_type = TEARDOWN;
                else if ((new String(request_type_string)).compareTo("OPTIONS") == 0)
                    request_type = OPTIONS;

                if (request_type == SETUP) {
                    // extract VideoFileName from RequestLine
                    VideoFileName = tokens.nextToken();
                }

                while (true) {
                    // parse the SeqNumLine and extract CSeq field
                    String line = RTSPBufferedReader.readLine();
                    System.out.println(line);

                    tokens = new StringTokenizer(line.trim());

                    if (!tokens.hasMoreTokens())
                        break;

                    String field = tokens.nextToken();

                    if (field.compareTo("CSeq:") == 0) {
                        RTSPSeqNb = Integer.parseInt(tokens.nextToken());
                    } else if (field.compareTo("Transport:") == 0) {
                        final String client_port = "client_port=";

                        // extract RTP_dest_port from LastLine
                        for (String str : tokens.nextToken().split(";")) {
                            if (str.startsWith(client_port)) {
                                String[] ports = str.substring(client_port.length()).split("-");
                                RTP_dest_port[0] = Integer.parseInt(ports[0]);
                                RTP_dest_port[1] = Integer.parseInt(ports[1]);
                            }
                        }
                    }
                }
            }
        } catch (IOException ex) {
            System.out.println("Exception caught: " + ex);
            System.exit(0);
        }
        return (request_type);
    }

    // ------------------------------------
    // Send RTSP Response
    // ------------------------------------
    private void send_RTSP_response() {
        try {
            RTSPBufferedWriter.write("RTSP/1.0 200 OK" + RtspConstants.CRLF);
            RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + RtspConstants.CRLF);
            RTSPBufferedWriter.write("Session: " + RTSP_ID + RtspConstants.CRLF);
            RTSPBufferedWriter.flush();
            // System.out.println("RTSP Server - Sent response to Client.");
        } catch (Exception ex) {
            System.out.println("Exception caught: " + ex);
            System.exit(0);
        }
    }

    private void send_RTSP_options_response() {
        try {
            RTSPBufferedWriter.write("RTSP/1.0 200 OK" + RtspConstants.CRLF);
            RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + RtspConstants.CRLF);
            // RTSPBufferedWriter.write("Session: " + RTSP_ID + CRLF);
            RTSPBufferedWriter.write("Public: DESCRIBE, SETUP, TEARDOWN, PLAY, PAUSE"
                    + RtspConstants.CRLF);
            RTSPBufferedWriter.flush();
        } catch (Exception ex) {
            System.out.println("Exception caught: " + ex);
            System.exit(0);
        }
    }

    /**
     * This method is used to build a minimal SDP file description.
     * 
     * @return
     * @throws UnknownHostException
     */
    public String getSdp(int track) throws UnknownHostException {

        StringBuffer buf = new StringBuffer();

        buf.append("v=0" + RtspConstants.CRLF);
        // filename contains leading slash
        buf.append("s=/video.mp4" + RtspConstants.CRLF);

        // cross encoder properties
        buf.append("m=video " + RtspConstants.CLIENT_VIDEO_PORT + RtspConstants.SEP + "RTP/AVP "
                + RtspConstants.RTP_H264_PAYLOADTYPE + RtspConstants.CRLF);

        buf.append("a=rtpmap:" + RtspConstants.RTP_H264_PAYLOADTYPE + RtspConstants.SEP
                + RtspConstants.H264 + RtspConstants.CRLF);

        buf.append("a=control:*" + RtspConstants.CRLF);

        /*
         * with change to in-band SPS/PPS parameters following SDP statements
         * should be unnecessary
         */
        // 176x144 15fps
        // sb.append("a=fmtp:" + RtspConstants.RTP_H264_PAYLOADTYPE +
        // " packetization-mode=0;" + H264Config.CODEC_PARAMS
        // +";sprop-parameter-sets=J0IAINoLExA,KM48gA==;" +
        // RtspResponse.CRLF);
        // 352 288 15fps
        // sb.append("a=fmtp:" + RtspConstants.RTP_H264_PAYLOADTYPE +
        // " packetization-mode=0;" + H264Config.CODEC_PARAMS
        // +";sprop-parameter-sets=J0IAINoFglE=,KM48gA==;" +
        // RtspResponse.CRLF);

        // buf.append("a=fmtp:98 packetization-mode=1;profile-level-id=420020;sprop-parameter-sets=J0IAIKaAoD0Q,KM48gA==;"
        // + RtspResponse.CRLF); // 640x480 20fps
        // buf.append("a=fmtp:98 packetization-mode=1;profile-level-id=420020;sprop-parameter-sets=J0IAINoLExA,KM48gA==;"
        // + RtspResponse.CRLF); // 176x144 15fps
        // sb.append("a=fmtp:" + RtspConstants.RTP_H264_PAYLOADTYPE +
        // " packetization-mode=1;" + H264Config.CODEC_PARAMS
        // +";sprop-parameter-sets=J0IAIKaCxMQ=,KM48gA==;" +
        // RtspResponse.CRLF); // 176x144 20fps
        // buf.append("a=fmtp:98 packetization-mode=1;profile-level-id=420020;sprop-parameter-sets=J0IAINoFB8Q=,KM48gA==;"
        // + RtspResponse.CRLF); // 320x240 10fps

        // additional information for android video view, due to extended
        // checking mechanism
        buf.append("a=framesize:" + RtspConstants.RTP_H264_PAYLOADTYPE + RtspConstants.SEP
                + RtspConstants.WIDTH + "-" + RtspConstants.HEIGHT + RtspConstants.CRLF);

        buf.append("a=control:trackID=" + String.valueOf(track));

        return buf.toString();
    }

    private void send_RTSP_describe_response() {
        try {
            // String sdp = RTSPProfile;
            String sdp = getSdp(1);

            RTSPBufferedWriter.write("RTSP/1.0 200 OK" + RtspConstants.CRLF);
            RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + RtspConstants.CRLF);

            RTSPBufferedWriter.write("Content-Type: application/sdp" + RtspConstants.CRLF);
            RTSPBufferedWriter.write("Content-Length: " + String.valueOf(sdp.length())
                    + RtspConstants.CRLF2);

            RTSPBufferedWriter.write(sdp);

            RTSPBufferedWriter.flush();

        } catch (Exception ex) {
            System.out.println("Exception caught: " + ex);
            System.exit(0);
        }
    }

    private void send_RTSP_setup_response() {
        try {
            RTSPBufferedWriter.write("RTSP/1.0 200 OK" + RtspConstants.CRLF);
            RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + RtspConstants.CRLF);
            RTSPBufferedWriter.write("Transport: RTP/AVP/UDP;unicast;client_port="
                    + RTP_dest_port[0] + "-" + RTP_dest_port[1] + ";server_port=9000-9001"
                    + RtspConstants.CRLF);
            RTSPBufferedWriter.write("Session: " + RTSP_ID + RtspConstants.CRLF);
            RTSPBufferedWriter.flush();
            // System.out.println("RTSP Server - Sent response to Client.");
        } catch (Exception ex) {
            System.out.println("Exception caught: " + ex);
            System.exit(0);
        }
    }
}
