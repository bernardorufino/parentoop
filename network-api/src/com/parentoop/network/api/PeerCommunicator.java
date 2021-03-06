package com.parentoop.network.api;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class PeerCommunicator {

    public static final byte MESSAGE_HEADER = 3;
    public static final byte FILE_HEADER = 4;
    public static final byte DISCONNECT_HEADER = 127;
    private final ScheduledExecutorService mExecutorService;

    protected Socket mSocket;
    private ObjectInputStream mInputStream;
    private ObjectOutputStream mOutputStream;
    private ClassLoader mClassLoader = ClassLoader.getSystemClassLoader();

    private Future<?> mReadTaskFuture;

    public PeerCommunicator(Socket socket, ScheduledExecutorService executorService) throws IOException {
        mSocket = socket;
        mOutputStream = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        mOutputStream.flush();
        mInputStream = new CustomObjectInputStream(new BufferedInputStream(socket.getInputStream()));
        mExecutorService = executorService;

        mReadTaskFuture = executorService.scheduleWithFixedDelay(new ReadRunnable(), 0, 100, TimeUnit.NANOSECONDS);
    }

    public InetAddress getAddress() {
        return mSocket.getInetAddress();
    }

    public void setClassLoader(ClassLoader classLoader) {
        if (classLoader == null) {
            mClassLoader = ClassLoader.getSystemClassLoader();
        } else {
            mClassLoader = classLoader;
        }
    }

    public synchronized void dispatchMessage(Message message) throws IOException {
        Object data = message.getData();
        if (data instanceof Path) {
            mOutputStream.writeByte(FILE_HEADER);
            mOutputStream.writeInt(message.getCode());
            FileTransferHelper.sendFile((Path) data, mOutputStream);
        } else if (data == null || data instanceof Serializable) {
            mOutputStream.writeByte(MESSAGE_HEADER);
            mOutputStream.writeInt(message.getCode());
            mOutputStream.writeObject(message.getData());
        }
        mOutputStream.flush();
    }

    protected void shutdown() throws IOException {
        mReadTaskFuture.cancel(true);
        // try with resources just for automatic closing
        try (InputStream is = mInputStream;
             OutputStream os = mOutputStream;
             Socket socket = mSocket) {
            os.write(DISCONNECT_HEADER);
            os.flush();
            mExecutorService.shutdownNow();
        } catch (IOException ex) {
            // eat exception, we are closing anyway
        }
    }

    private void readMessage() throws IOException, ClassNotFoundException {
        byte header = mInputStream.readByte();
        Message message;
        switch (header) {
            case MESSAGE_HEADER:
                message = new Message(mInputStream.readInt(), mInputStream.readObject());
                handleMessage(message);
                break;
            case FILE_HEADER:
                int messageType = mInputStream.readInt();
                Path fileRead = Files.createTempFile("parentoop", ".tempfile");
                fileRead.toFile().deleteOnExit();
                FileTransferHelper.receiveFile(fileRead, mInputStream);
                message = new Message(messageType, fileRead);
                handleMessage(message);
                break;
            case DISCONNECT_HEADER:
                shutdown();
                break;
        }
    }

    protected abstract void handleMessage(Message message);

    private class ReadRunnable implements Runnable {

        @Override
        public void run() {
            try {
                if (mSocket.isClosed() || mSocket.isInputShutdown() || mSocket.isOutputShutdown()) {
                    shutdown();
                    return;
                }
                try {
                    if (mInputStream.available() == 0) return;
                    readMessage();
                } catch (EOFException ex) {
                    // end of stream, shutdown
                    shutdown();
                } catch (IOException ex) {
                    String excMessage = ex.getMessage();
                    if (excMessage == null) excMessage = "";
                    if (mSocket.isClosed() || excMessage.contains("closed")) {
                        shutdown();
                    } else {
                        throw ex;
                    }
                }
            } catch (IOException | ClassNotFoundException ex) {
                ex.printStackTrace();
            }
        }
    }

    private class CustomObjectInputStream extends ObjectInputStream {

        public CustomObjectInputStream(InputStream in) throws IOException {
            super(in);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            try {
                return Class.forName(desc.getName(), true, mClassLoader);
            } catch (ClassNotFoundException ex) {
                return super.resolveClass(desc);
            }
        }
    }
}
