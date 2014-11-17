package com.parentoop.slave.executor.phases;

import com.parentoop.core.networking.Messages;
import com.parentoop.core.networking.Ports;
import com.parentoop.network.api.Message;
import com.parentoop.network.api.NodeClient;
import com.parentoop.network.api.PeerCommunicator;
import com.parentoop.slave.executor.PhaseExecutor;
import com.parentoop.slave.executor.TaskParameters;
import com.parentoop.slave.view.Console;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

public abstract class Phase {

    protected static final long INFINITY_TIME_OUT = 60 * 60 * 24 * 7; // In seconds

    private Class<? extends Phase> mNextPhaseClass;
    private Map<InetAddress, NodeClient> mPeers = new HashMap<>();
    protected PhaseExecutor mExecutor;
    protected NodeClient mMasterConnection;

    protected Phase() {
        mNextPhaseClass = getClass();
    }

    public void initialize(TaskParameters parameters) {
        mMasterConnection = parameters.getMasterConnection();
        mExecutor = parameters.getExecutor();
    }

    public void terminate(TaskParameters parameters) {
        /* No-op */
    }

    public abstract void execute(Message message, PeerCommunicator sender);

    public Phase nextPhase() {
        if (mNextPhaseClass.equals(getClass())) return this;
        try {
            return mNextPhaseClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    protected <T extends Phase> void nextPhase(Class<T> phaseClass) {
        mNextPhaseClass = phaseClass;
    }

    protected void dispatchMessageToSlave(InetAddress slaveAddress, Message message) {
        try {
            Console.println("-> " + message.getCode());
            NodeClient nodeClient = mPeers.get(slaveAddress);
            if (nodeClient == null) {
                nodeClient = new NodeClient(slaveAddress, Ports.SLAVE_SLAVE_PORT, mExecutor);
                mPeers.put(slaveAddress, nodeClient);
            }
            nodeClient.dispatchMessage(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void respondToSlave(PeerCommunicator slave, Message message) {
        try {
            Console.println(message.getCode() + " -> SLAVE");
            slave.dispatchMessage(message);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    protected void dispatchMessageToMaster(Message message) {
        try {
            Console.println(message.getCode() + " -> MASTER");
            mMasterConnection.dispatchMessage(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void closeSlaves() {
        for (NodeClient slave : mPeers.values()) {
            try {
                slave.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void dispatchIdleMessage() {
        Console.println("IDLE -> Master");
        try {
            mMasterConnection.dispatchMessage(new Message(Messages.IDLE));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
