package ch.epfl.tkvs.transactionmanager.algorithms;

import java.io.IOException;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

import ch.epfl.tkvs.exceptions.CommitWithoutPrepareException;
import ch.epfl.tkvs.exceptions.TransactionAlreadyExistsException;
import ch.epfl.tkvs.exceptions.TransactionNotLiveException;
import ch.epfl.tkvs.transactionmanager.Transaction;
import ch.epfl.tkvs.transactionmanager.TransactionManager;
import ch.epfl.tkvs.transactionmanager.Transaction_2PL;
import ch.epfl.tkvs.transactionmanager.communication.DeadlockInfoMessage;
import ch.epfl.tkvs.transactionmanager.communication.requests.AbortRequest;
import ch.epfl.tkvs.transactionmanager.communication.requests.BeginRequest;
import ch.epfl.tkvs.transactionmanager.communication.requests.CommitRequest;
import ch.epfl.tkvs.transactionmanager.communication.responses.GenericSuccessResponse;
import ch.epfl.tkvs.transactionmanager.lockingunit.DeadlockGraph;
import ch.epfl.tkvs.transactionmanager.lockingunit.DeadlockInfo;
import ch.epfl.tkvs.transactionmanager.lockingunit.LockingUnit;
import ch.epfl.tkvs.transactionmanager.versioningunit.VersioningUnitMVCC2PL;
import ch.epfl.tkvs.yarn.HDFSLogger;


public abstract class Algo2PL extends CCAlgorithm {

    protected LockingUnit lockingUnit;
    protected VersioningUnitMVCC2PL versioningUnit;

    // Datastructure which maps transaction id to a Transaction_2PL object
    protected ConcurrentHashMap<Integer, Transaction_2PL> transactions;
    private final HDFSLogger log;

    public Algo2PL(RemoteHandler remote, HDFSLogger log) {
        super(remote, log);
        this.log = log;

        lockingUnit = LockingUnit.instance;
        versioningUnit = VersioningUnitMVCC2PL.getInstance();
        versioningUnit.init();
        transactions = new ConcurrentHashMap<>();
    }

    // Does cleaning up after end of transaction
    protected void terminate(Transaction_2PL transaction, boolean success) {
        if (success) {
            versioningUnit.commit(transaction.transactionId);
        } else {
            versioningUnit.abort(transaction.transactionId);

        }
        lockingUnit.releaseAll(transaction.transactionId, transaction.getCurrentLocks());
        if (!success && !isLocalTransaction(transaction))
            remote.abortOthers(transaction);
        transactions.remove(transaction.transactionId);
    }

    @Override
    public Transaction getTransaction(int xid) {
        return transactions.get(xid);
    }

    @Override
    public GenericSuccessResponse abort(AbortRequest request) {
        int xid = request.getTransactionId();

        Transaction_2PL transaction = transactions.get(xid);

        // Transaction not begun or already terminated
        if (transaction == null) {
            return new GenericSuccessResponse(new TransactionNotLiveException());
        }

        // if there was any thread waiting, that thread would throw abort, no need to terminate in this thread.
        if (!lockingUnit.interruptWaitingLocks(xid))
            terminate(transaction, false);
        return new GenericSuccessResponse();
    }

    @Override
    public GenericSuccessResponse begin(BeginRequest request) {
        int xid = request.getTransactionId();

        // Transaction with duplicate id
        if (transactions.containsKey(xid)) {
            return new GenericSuccessResponse(new TransactionAlreadyExistsException());
        }
        transactions.put(xid, new Transaction_2PL(xid));

        return new GenericSuccessResponse();
    }

    @Override
    public GenericSuccessResponse commit(CommitRequest request) {
        int xid = request.getTransactionId();

        Transaction_2PL transaction = transactions.get(xid);

        // Transaction not begun or already terminated or not prepared
        if (transaction == null) {
            return new GenericSuccessResponse(new TransactionNotLiveException());
        }

        if (!transaction.isPrepared) {
            return new GenericSuccessResponse(new CommitWithoutPrepareException());
        }
        terminate(transaction, true);
        return new GenericSuccessResponse();

    }

    @Override
    public void checkpoint() {
        // Get the dead lock graph
        DeadlockGraph graph = lockingUnit.getDeadlockGraph();

        try {
            // Create the message
            DeadlockInfo di = new DeadlockInfo(TransactionManager.getLocalityHash(), graph, new HashSet<>(transactions.keySet()));
            DeadlockInfoMessage deadlockMessage = new DeadlockInfoMessage(di);
            log.info("About to send deadlock info to app master: " + deadlockMessage, Algo2PL.class);
            TransactionManager.sendToAppMaster(deadlockMessage, false);

        } catch (IOException e) {
            log.error("Error", e, Algo2PL.class);
        }
    }
}
