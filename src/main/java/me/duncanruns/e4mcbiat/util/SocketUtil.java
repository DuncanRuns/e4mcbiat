package me.duncanruns.e4mcbiat.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.util.Arrays;

public class SocketUtil {
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    /**
     * @param max the maximum number of bytes to read.
     * @return a byte array with the length of the bytes that were read, or null if the end of stream has been reached.
     */
    public static byte[] readAny(InputStream stream, int max) throws IOException {
        byte[] buf = new byte[max];
        int actuallyRead = stream.read(buf);
        if (actuallyRead == -1) return null;
        return Arrays.copyOfRange(buf, 0, actuallyRead);
    }


    /**
     * @param stream the input stream, expected to be from a socket
     * @param total  the total amount of bits to receive
     * @return a byte array of length `total`, or null
     */
    public static byte[] readSpecific(InputStream stream, int total) throws IOException {
        byte[] buf = new byte[total];
        int off = 0;
        int offAdd;
        while ((offAdd = stream.read(buf, off, total - off)) != -1) {
            off += offAdd;
            if (off == total) return buf;
        }
        return null;
    }

    /**
     * Used to check if port is in use to avoid errors due to overlapping ports.
     * Will attempt to create an empty unused socket with the port, and immediately close it.
     *
     * @param portNum the port to test
     * @return true if the socket is free, otherwise false
     */
    public static boolean isPortFree(int portNum) {
        try (ServerSocket test = new ServerSocket(portNum)) {
            // if no exception thrown, port is open
            test.close(); // currently closes socket as I'm not 100% sure that this will allow a new socket in the same
            SocketUtil.carelesslyClose(test);
            // place as the test
            return true;
        } catch (IOException testExc) {
            //if exception is thrown by creating a socket, it means the port is busy
            return false;
        }
    }

    public static void carelesslyClose(Closeable socket) {
        if (socket == null) return;
        try {
            socket.close();
        } catch (Throwable ignored) {
        }
    }

    public static String bytesToHexString(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

}