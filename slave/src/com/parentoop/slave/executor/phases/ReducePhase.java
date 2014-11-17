package com.parentoop.slave.executor.phases;

import com.parentoop.core.api.Reducer;
import com.parentoop.core.data.DataPool;
import com.parentoop.core.data.Datum;
import com.parentoop.core.networking.Messages;
import com.parentoop.network.api.Message;
import com.parentoop.network.api.PeerCommunicator;
import com.parentoop.slave.api.SlaveStorage;
import com.parentoop.slave.executor.TaskParameters;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;

public class ReducePhase extends Phase {

    private final ExecutorService mReducersThreadPool = Executors.newCachedThreadPool();
    private final ExecutorService mCollectorsThreadPool = Executors.newCachedThreadPool();
    private final ExecutorService mValueSendersThreadPool = Executors.newCachedThreadPool();

    private SlaveStorage<Serializable> mStorage;
    private Reducer mReducer;

    private Collection<InetAddress> mSlaveAddresses;
    private List<String> mKeys;
    private Map<String, DataPool> mValues = new HashMap<>();
    private Map<String, Future<Serializable>> mResults = new HashMap<>();
    private Map<String, Integer> mRequests = new HashMap<>();
    private int mTotalRequests = 0;

    @Override
    public void initialize(TaskParameters parameters) {
        super.initialize(parameters);
        mStorage = parameters.getStorage();
        mReducer = parameters.getReducer();
    }

    @Override
    public void terminate(TaskParameters parameters) {
        mStorage.terminate();
        super.terminate(parameters);
    }

    @Override
    public void execute(Message message, PeerCommunicator sender) {
        switch (message.getCode()) {
            case Messages.LOAD_SLAVE_ADDRESSES: // param = { address }
                mSlaveAddresses = Arrays.asList(message.<InetAddress[]>getData());
                break;
            case Messages.REDUCE_KEYS: // param = { key }
                mKeys = Arrays.asList(message.<String[]>getData());
                startReduce();
                break;
            case Messages.KEY_VALUE: // param = (key, value)
                Datum datum = message.getData();
                //noinspection unchecked
                mValues.get(datum.getKey()).yield(datum.getValue());
                break;
            case Messages.REQUEST_VALUES: // param = key
                ValueSender task = new ValueSender(sender, message.<String>getData());
                mValueSendersThreadPool.submit(task);
                break;
            case Messages.END_OF_DATA_STREAM: // param = key
                String key = message.getData();
                int n = mRequests.get(key) - 1;
                mRequests.put(key, n);
                if (n == 0) {
                    mValues.get(key).close();
                    mCollectorsThreadPool.submit(new CollectResultTask(key));
                }
                mTotalRequests--;
                if (mTotalRequests == 0) {
                    mCollectorsThreadPool.shutdownNow();
                    dispatchMessageToMaster(new Message(Messages.END_OF_RESULT_STREAM));
                    nextPhase(LoadPhase.class);
                    dispatchIdleMessage();
                }
        }
    }

    private void startReduce() {
        for (String key : mKeys) {
            DataPool pool = new DataPool();
            mValues.put(key, pool);
            requestValues(key);
            Future<Serializable> result = mReducersThreadPool.submit(new ReduceTask(key, pool));
            mResults.put(key, result);
        }
    }

    private void requestValues(String key) {
        int size = mSlaveAddresses.size();
        mRequests.put(key, size);
        mTotalRequests += size;
        for (InetAddress slaveAddress : mSlaveAddresses) {
            dispatchMessageToSlave(slaveAddress, new Message(Messages.REQUEST_VALUES, key));
        }
    }

    private class ReduceTask implements Callable<Serializable> {

        private final String mKey;
        private final DataPool mDataPool;

        private ReduceTask(String key, DataPool dataPool) {
            mKey = key;
            mDataPool = dataPool;
        }

        @Override
        public Serializable call() throws Exception {
            //noinspection unchecked
            return mReducer.reduce(mKey, mDataPool);
        }
    }

    private class ValueSender implements Runnable {

        private final String mKey;
        private final PeerCommunicator mRequester;

        private ValueSender(PeerCommunicator requester, String key) {
            mRequester = requester;
            mKey = key;
        }

        @Override
        public void run() {
            for (Serializable value : mStorage.read(mKey)) {
                respondToSlave(mRequester, new Message(Messages.KEY_VALUE, new Datum(mKey, value)));
            }
            respondToSlave(mRequester, new Message(Messages.END_OF_DATA_STREAM, mKey));
        }
    }

    private class CollectResultTask implements Runnable {

        private final String mKey;

        public CollectResultTask(String key) {
            mKey = key;
        }

        @Override
        public void run() {
            Future<Serializable> future = mResults.get(mKey);
            try {
                Datum datum = new Datum(mKey, future.get());
                dispatchMessageToMaster(new Message(Messages.RESULT_PAIR, datum));
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
    }
}
