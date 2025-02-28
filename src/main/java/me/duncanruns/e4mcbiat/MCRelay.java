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
    private boolean closed = false;
    private Socket socket = null;
    private final int port;

    public MCRelay(QuicStream stream, Consumer<MCRelay> onClose) {
        this(stream, onClose, 25565);
    }

    public MCRelay(QuicStream stream, Consumer<MCRelay> onClose, int port) {
        this.onClose = onClose;
        this.stream = stream;
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
        System.out.println("Opened MCRelay receiver for " + stream);
        try {
            transferLoop(stream.getInputStream(), socket.getOutputStream());
        } catch (IOException e) {
            close();
        }
    }

    private void runSender() {
        System.out.println("Opened MCRelay sender for " + stream);
        try {
            transferLoop(socket.getInputStream(), stream.getOutputStream());
        } catch (IOException e) {
            close();
        }
    }

    private void transferLoop(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[16384];
        while (!closed) {
            int read = in.read(buffer);
            if (read == -1) close();
            else if (read > 0) out.write(buffer, 0, read);
        }
    }

    public synchronized void close() {
        if (closed) return;
        System.out.println("Closing MCRelay for " + stream);
        closed = true;
        carelesslyClose(stream.getInputStream());
        carelesslyClose(stream.getOutputStream());
        carelesslyClose(socket);
        onClose.accept(this);
    }
}
