package com.taikodrum.controller;

import android.os.Handler;
import android.os.Looper;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

final class DrumClient implements Closeable {
    interface StatusListener {
        void onStatus(String message, boolean error);
    }

    static final int DEFAULT_PORT = 27183;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger sequence = new AtomicInteger();

    private DatagramSocket socket;
    private InetAddress address;
    private int port = DEFAULT_PORT;
    private String token = "";
    private StatusListener listener;

    synchronized void setStatusListener(StatusListener listener) {
        this.listener = listener;
    }

    synchronized void configure(String host, int port, String token) throws IOException {
        if (host == null || host.trim().isEmpty()) {
            throw new IOException("PC IP is empty");
        }

        this.address = InetAddress.getByName(host.trim());
        this.port = port;
        this.token = token == null ? "" : token.trim();

        if (socket == null || socket.isClosed()) {
            socket = new DatagramSocket();
            socket.setTrafficClass(0x10);
        }

        postStatus("OK " + this.address.getHostAddress(), false);
    }

    void sendKey(String key, boolean pressed) {
        final InetAddress targetAddress;
        final int targetPort;
        final String targetToken;
        final DatagramSocket targetSocket;

        synchronized (this) {
            targetAddress = address;
            targetPort = port;
            targetToken = token;
            targetSocket = socket;
        }

        if (targetAddress == null || targetSocket == null || targetSocket.isClosed()) {
            postStatus("Set PC IP or USB mode first", true);
            return;
        }

        String action = pressed ? "DOWN" : "UP";
        int seq = sequence.incrementAndGet();
        long now = System.currentTimeMillis();
        String packetText = String.format(
                Locale.US,
                "TKD1|%s|%d|%s|%s|%d",
                targetToken,
                seq,
                key,
                action,
                now
        );

        executor.execute(() -> {
            try {
                byte[] payload = packetText.getBytes(StandardCharsets.US_ASCII);
                DatagramPacket packet = new DatagramPacket(
                        payload,
                        payload.length,
                        targetAddress,
                        targetPort
                );
                targetSocket.send(packet);
            } catch (IOException ex) {
                postStatus("Send failed: " + ex.getMessage(), true);
            }
        });
    }

    void sendTap(String key) {
        sendKey(key, true);
        mainHandler.postDelayed(() -> sendKey(key, false), 35);
    }

    private void postStatus(String message, boolean error) {
        final StatusListener callback;
        synchronized (this) {
            callback = listener;
        }

        if (callback != null) {
            mainHandler.post(() -> callback.onStatus(message, error));
        }
    }

    @Override
    public synchronized void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        executor.shutdownNow();
    }
}
