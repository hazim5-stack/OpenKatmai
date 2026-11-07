package org.openkatmai.tiles;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Fixed local endpoint; callers cannot supply endpoints or shell text. */
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
            socket.setSoTimeout(55000);
            input = new DataInputStream(socket.getInputStream());
            output = socket.getOutputStream();
            send(CNXN, 0x01000000, 4096, "host::openkatmai-tiles\0".getBytes(StandardCharsets.UTF_8));
            Packet p = receive();
            if (p.command == AUTH) throw new IOException("The local ADB service requires authorization.");
            if (p.command != CNXN) throw new IOException("The local ADB handshake failed.");
        } catch (IOException e) { socket.close(); throw e; }
    }
    private void requireSystem() throws IOException {
        String uid=shell("id -u").trim();
        if (!"1000".equals(uid)) throw new IOException("Expected UID 1000; got " + uid);
    }
    String launch64() throws IOException {
        requireSystem();
        String component=Config.ARM64_COMPONENT;
        if (!component.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.]+"))
            throw new IOException("Invalid configured activity component");
        return shell("/system/bin/sh /data/data/com.dpad.fuli/launch64.sh " + component);
    }
    String openShell() throws IOException {
        requireSystem();
        return shell("am start -W -n com.dpad.fuli/.ShellCommandActivity");
    }
    private String shell(String command) throws IOException {
        int local = stream++;
        send(OPEN, local, 0, ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        while (true) {
            Packet p = receive();
            if (p.b != local) throw new IOException("Unexpected ADB stream.");
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
