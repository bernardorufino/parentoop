package com.parentoop.client.masterproxy;

import com.parentoop.core.networking.Messages;
import com.parentoop.network.api.Message;
import com.parentoop.network.api.PeerCommunicator;
import com.parentoop.network.api.messaging.MessageHandler;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class MasterMessageHandler implements MessageHandler {

    private MasterProxy mProxy;
    PrintStream mPrintStream;
    String mOutputName;
    private int mSlaveCount = 0;

    public MasterMessageHandler(PrintStream printStream, String outputName) {
        mPrintStream = printStream;
        mOutputName = outputName;
    }

    public void setProxy(MasterProxy proxy) {
        mProxy = proxy;
    }

    @Override
    public final void handle(Message message, PeerCommunicator sender) {
        int type = message.getCode();
        switch (type) {
            case Messages.LOAD_JAR:
                handleLoadJar(message, sender);
                break;
            case Messages.SETTING_UP:
                // TODO: Make master return the number of slaves rather than counting them here
                mPrintStream.println("-- Starting task with " + mSlaveCount +" slaves");
                break;
            case Messages.FAILURE:
                mPrintStream.print("Task failed. Master's node response:");
                mPrintStream.println((String) message.getData());
                System.exit(0);
                break;
            case Messages.MAPPING:
                mPrintStream.println("-- Mapping...");
                break;
            case Messages.REDUCING:
                mPrintStream.println("-- Reducing...");
                break;
            case Messages.SLAVE_CONNECTED:
                mPrintStream.println("-- Slave " + message.getData() + " connected");
                mSlaveCount++;
                break;
            case Messages.SLAVE_DISCONNECTED:
                mPrintStream.println("-- Slave " + message.getData() + " disconnected");
                mSlaveCount--;
            case Messages.SEND_RESULT:
                handleSendResult(message, sender);
                break;
        }
    }

    protected void handleLoadJar(Message message, PeerCommunicator sender) {
        // Not removing this method because of test legacy reasons
        mPrintStream.println((String) message.getData());
    }

    protected void handleSendResult(Message message, PeerCommunicator sender){
        Path receivedResult = message.getData();
        try {
            Files.copy(receivedResult, Paths.get(mOutputName).toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mPrintStream.println("\nResult saved on \"" + mOutputName + "\"\n");
        try {
            mProxy.shutdown();
        } catch (IOException e) {
            System.exit(1); // shutting down anyway
        }
    }


}
