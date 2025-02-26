package me.duncanruns.e4mcbiat;

import tech.kwik.core.QuicStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.function.Consumer;

import static me.duncanruns.e4mcbiat.util.SocketUtil.carelesslyClose;

public class MCRelay {
    private final Consumer<MCRelay> onClose;
    private final QuicStream stream;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private boolean closed = false;
    private Socket socket = null;
    private final int port;

    public MCRelay(QuicStream stream, Consumer<MCRelay> onClose) {
        this(stream, onClose, 25565);
    }

    public MCRelay(QuicStream stream, Consumer<MCRelay> onClose, int port) {
        this.onClose = onClose;
        this.stream = stream;
        inputStream = stream.getInputStream();
        outputStream = stream.getOutputStream();
        this.port = port;
    }

    private boolean openSocket() {
        try {
            socket = new Socket("localhost", port);
            return true;
        } catch (IOException e) {
            close();
        }
        return false;
    }

    public synchronized void start() {
        if (!openSocket()) {
            close();
            return;
        }
        Thread receiverThread = new Thread(this::runReceiver, stream + "-receiver");
        receiverThread.setDaemon(true);
        receiverThread.start();
        Thread senderThread = new Thread(this::runSender, stream + "-sender");
        senderThread.setDaemon(true);
        senderThread.start();
    }

    private void runReceiver() {
        try {
            while (!closed) inputStream.transferTo(socket.getOutputStream());
        } catch (IOException e) {
            close();
        }
    }

    private void runSender() {
        try {
            while (!closed) socket.getInputStream().transferTo(outputStream);
        } catch (IOException e) {
            close();
        }
    }

    public synchronized void close() {
        if (closed) return;
        closed = true;
        carelesslyClose(inputStream);
        carelesslyClose(outputStream);
        carelesslyClose(socket);
        onClose.accept(this);
    }
}
