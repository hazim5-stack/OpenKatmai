package org.openkatmai.toolbox;

import java.io.ByteArrayOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Package-private transport used only by fixed Toolbox actions; loopback endpoint only. */
final class LocalAdb implements Closeable {
    private final Socket socket = new Socket();
    private final DataInputStream input;
    private final OutputStream output;
    private int stream = 1;
    private static final int CNXN=0x4e584e43, AUTH=0x48545541, OPEN=0x4e45504f,
            OKAY=0x59414b4f, WRTE=0x45545257, CLSE=0x45534c43;
    private static final class Packet {
        int command, a, b;
        byte[] data;
    }
    LocalAdb() throws IOException {
        try {
            socket.connect(new InetSocketAddress("127.0.0.1", 5555), 2500);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(55000);
            input = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 65536));
            output = new BufferedOutputStream(socket.getOutputStream(), 65536);
            send(CNXN, 0x01000000, 262144, "host::openkatmai-toolbox\0".getBytes(StandardCharsets.UTF_8));
            Packet p = receive();
            if (p.command == AUTH) throw new IOException("The local ADB service requires authorization.");
            if (p.command != CNXN) throw new IOException("The local ADB handshake failed.");
        } catch (IOException e) { socket.close(); throw e; }
    }
    private void requireSystem() throws IOException {
        String uid=shell("id -u").trim();
        if (!"1000".equals(uid)) throw new IOException("Expected UID 1000; got " + uid);
    }
    String execute(String command) throws IOException {
        requireSystem();
        return shell(command);
    }
    /** Raw exec avoids PTY newline conversion when inspecting an APK. Streams to disk. */
    void download(String path, OutputStream target) throws IOException {
        download(path, target, null);
    }
    interface TransferProgress { void update(long bytes); }
    void download(String path, OutputStream target, TransferProgress progress) throws IOException {
        Actions.downloadPath(path);
        requireSystem();
        int local = stream++;
        send(OPEN, local, 0, ("exec:cat " + Actions.quote(path) + "\0").getBytes(StandardCharsets.UTF_8));
        long total = 0, lastProgress = 0, deadline = System.nanoTime() + 600000000000L;
        while (true) {
            long remaining = (deadline - System.nanoTime()) / 1000000L;
            if (remaining <= 0) throw new IOException("APK read timed out");
            socket.setSoTimeout((int)Math.min(55000, remaining));
            Packet p = receive();
            if (p.b != local) {
                if (p.b < local && (p.command == CLSE || p.command == OKAY)) continue;
                throw new IOException("Unexpected APK stream");
            }
            if (p.command == OKAY) continue;
            if (p.command == WRTE) {
                total += p.data.length;
                if (total > 1073741824L) throw new IOException("APK exceeds 1 GiB inspection limit");
                target.write(p.data); send(OKAY, local, p.a, new byte[0]);
                long now = System.nanoTime();
                if (progress != null && now - lastProgress >= 250000000L) {
                    progress.update(total); lastProgress = now;
                }
            } else if (p.command == CLSE) {
                send(CLSE, local, p.a, new byte[0]);
                if (total == 0) throw new IOException("APK is empty or cannot be read");
                if (progress != null) progress.update(total);
                return;
            } else throw new IOException("Unexpected APK packet");
        }
    }
    private String shell(String command) throws IOException {
        int local = stream++;
        // Raw exec does not allocate a PTY. A watchdog must not keep a terminal-backed
        // stream alive after launch64 has finished and restored its state.
        send(OPEN, local, 0, ("exec:sh -c " + Actions.quote(command) + "\0").getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        long deadline = System.nanoTime() + 60000000000L;
        while (true) {
            long remaining = (deadline - System.nanoTime()) / 1000000L;
            if (remaining <= 0) throw new IOException("ADB operation timed out");
            socket.setSoTimeout((int)Math.min(55000, remaining));
            Packet p = receive();
            if (p.b != local) {
                if (p.b < local && (p.command == CLSE || p.command == OKAY)) continue;
                throw new IOException("Unexpected ADB stream: " + p.b + ", expected " + local);
            }
            if (p.command == OKAY) continue;
            if (p.command == WRTE) {
                if (result.size() + p.data.length > 65536) throw new IOException("Unexpectedly large response.");
                result.write(p.data, 0, p.data.length);
                send(OKAY, local, p.a, new byte[0]);
            } else if (p.command == CLSE) {
                send(CLSE, local, p.a, new byte[0]);
                return new String(result.toByteArray(), StandardCharsets.UTF_8);
            } else throw new IOException("Unexpected ADB message.");
        }
    }
    private int number() throws IOException { return Integer.reverseBytes(input.readInt()); }
    private Packet receive() throws IOException {
        Packet p = new Packet();
        p.command=number(); p.a=number(); p.b=number();
        int size=number(), checksum=number(), magic=number();
        if (magic != (p.command ^ -1) || size < 0 || size > 1048576) throw new IOException("Invalid ADB packet.");
        p.data = new byte[size]; input.readFully(p.data);
        int sum=0; for (byte b : p.data) sum += b & 255;
        if (checksum != 0 && checksum != sum) throw new IOException("ADB checksum mismatch.");
        return p;
    }
    private void number(int v) throws IOException {
        output.write(v & 255); output.write((v >>> 8) & 255);
        output.write((v >>> 16) & 255); output.write((v >>> 24) & 255);
    }
    private void send(int cmd, int a, int b, byte[] data) throws IOException {
        int sum=0; for (byte v : data) sum += v & 255;
        number(cmd);number(a);number(b);number(data.length);number(sum);number(cmd ^ -1);
        output.write(data);output.flush();
    }
    @Override public void close() throws IOException { socket.close(); }
}
