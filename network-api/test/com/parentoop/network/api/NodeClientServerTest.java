package com.parentoop.network.api;

import com.parentoop.network.api.messaging.MessageHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.*;

public class NodeClientServerTest {

    private static final int PORT = 13371;
    private static final long FAKE_NETWORK_THRESHOLD = 100;

    private NodeServer mNodeServer;
    private NodeClient mNodeClient;

    private ReentrantLock mLock;
    private Condition mCondition;
    private Message mReceivedMessage;

    @Before
    public void setUp() throws IOException, InterruptedException {
        mNodeServer = new NodeServer(PORT, new MessageReceiveSignaler());
        mNodeServer.startServer();
        mNodeClient = new NodeClient(InetAddress.getLocalHost(), PORT, new MessageReceiveSignaler());
        Thread.sleep(FAKE_NETWORK_THRESHOLD);

        mLock = new ReentrantLock();
        mCondition = mLock.newCondition();
    }

    @After
    public void tearDown() throws IOException, InterruptedException {
        if (mNodeClient != null) mNodeClient.shutdown();
        if (mNodeServer != null) mNodeServer.shutdown();
        Thread.sleep(FAKE_NETWORK_THRESHOLD);

        mLock = null;
        mReceivedMessage = null;
        mCondition = null;
    }

    @Test
    public void testClientConnection() throws IOException, InterruptedException {
        assertEquals(1, mNodeServer.getConnectedPeers().size());

        mNodeClient.shutdown();
        mNodeClient = null;
        Thread.sleep(FAKE_NETWORK_THRESHOLD);
        assertEquals(0, mNodeServer.getConnectedPeers().size());
    }

    @Test
    public void testClientMessageReceived() throws IOException, InterruptedException {
        Message message = new Message(10, "mensagem");
        mNodeClient.dispatchMessage(message);

        waitForMessageReceive();
        assertNotNull(mReceivedMessage);
        assertNotSame(message, mReceivedMessage);
        assertEquals(message.getType(), mReceivedMessage.getType());
        assertEquals(message.getData(), mReceivedMessage.getData());
    }

    @Test
    public void testServerMessageReceived() throws IOException, InterruptedException {
        Message message = new Message(12, "mensagem_server");
        mNodeServer.broadcastMessage(message);

        waitForMessageReceive();
        assertNotNull(mReceivedMessage);
        assertNotSame(message, mReceivedMessage);
        assertEquals(message.getType(), mReceivedMessage.getType());
        assertEquals(message.getData(), mReceivedMessage.getData());
    }

    @Test
    public void testMultipleMessages() throws IOException, InterruptedException {
        Message message = new Message(10, "mensagem");
        mNodeClient.dispatchMessage(message);
        waitForMessageReceive();
        assertNotNull(mReceivedMessage);
        assertNotSame(message, mReceivedMessage);
        assertEquals(message.getType(), mReceivedMessage.getType());
        assertEquals(message.getData(), mReceivedMessage.getData());

        mReceivedMessage = null;
        message = new Message(11);
        mNodeClient.dispatchMessage(message);
        waitForMessageReceive();
        assertNotNull(mReceivedMessage);
        assertNotSame(message, mReceivedMessage);
        assertEquals(message.getType(), mReceivedMessage.getType());
        assertEquals(message.getData(), mReceivedMessage.getData());

        mReceivedMessage = null;
        message = new Message(20, 45);
        mNodeServer.broadcastMessage(message);
        waitForMessageReceive();
        assertNotNull(mReceivedMessage);
        assertNotSame(message, mReceivedMessage);
        assertEquals(message.getType(), mReceivedMessage.getType());
        assertEquals(message.getData(), mReceivedMessage.getData());
    }

    @Test
    public void testFileSharing() throws IOException, InterruptedException {
        String loremIpsum = "Lorem ipsum dolor sit amet.";

        Path fileToSend = Files.createTempFile("dummy", ".txt");
        fileToSend.toFile().deleteOnExit();
        PrintWriter out = new PrintWriter(Files.newOutputStream(fileToSend));
        out.println(loremIpsum);
        out.close();

        mNodeClient.dispatchMessage(new Message(10, fileToSend));
        waitForMessageReceive();
        assertNotNull(mReceivedMessage);
        assertEquals(10, mReceivedMessage.getType());

        Path receivedFile = mReceivedMessage.getData();
        Scanner in = new Scanner(Files.newInputStream(receivedFile));
        String read = in.nextLine();
        in.close();
        assertEquals(loremIpsum, read);
    }

    private void waitForMessageReceive() throws InterruptedException {
        mLock.lock();
        try {
            mCondition.await(FAKE_NETWORK_THRESHOLD, TimeUnit.MILLISECONDS);
        } finally {
            mLock.unlock();
        }
    }

    private class MessageReceiveSignaler implements MessageHandler {
        @Override
        public void handle(Message message, PeerCommunicator sender) {
            mLock.lock();
            try {
                mReceivedMessage = message;
                mCondition.signal();
            } finally {
                mLock.unlock();
            }
        }
    }
}
