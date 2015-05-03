/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.epfl.tkvs.transactionmanager.algorithms;

import ch.epfl.tkvs.ScheduledTestCase;
import ch.epfl.tkvs.transactionmanager.communication.requests.BeginRequest;
import ch.epfl.tkvs.transactionmanager.communication.requests.CommitRequest;
import ch.epfl.tkvs.transactionmanager.communication.requests.ReadRequest;
import ch.epfl.tkvs.transactionmanager.communication.requests.WriteRequest;
import ch.epfl.tkvs.transactionmanager.communication.responses.GenericSuccessResponse;
import ch.epfl.tkvs.transactionmanager.communication.responses.ReadResponse;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.fail;
import org.junit.Before;
import org.junit.Test;


public class Simple2PLTest extends ScheduledTestCase {

    private static Simple2PL instance;

    @Before
    public void setUp() {
        instance = new Simple2PL();
    }

    @Test
    public void testRead() {
        try {

            ReadRequest request = new ReadRequest(0, 0, 0);

            ReadResponse result = instance.read(request);
            assertEquals(false, result.getSuccess());

            GenericSuccessResponse br;
            br = instance.begin(new BeginRequest(0));
            assertEquals(true, br.getSuccess());

            result = instance.read(request);
            assertEquals(true, result.getSuccess());
            assertEquals(null, result.getValue());

        } catch (IOException ex) {
            fail(ex.getLocalizedMessage());
        }
    }

    /**
     * Test of write method, of class MVCC2PL.
     */
    @Test
    public void testWrite() {

        try {
            WriteRequest request = new WriteRequest(0, 0, 0, 0);

            GenericSuccessResponse result = instance.write(request);
            assertEquals(false, result.getSuccess());

            GenericSuccessResponse br;
            br = instance.begin(new BeginRequest(0));
            assertEquals(true, br.getSuccess());

            result = instance.write(request);
            assertEquals(true, result.getSuccess());

        } catch (IOException ex) {
            fail(ex.getLocalizedMessage());
        }
    }

    /**
     * Test of begin method, of class MVCC2PL.
     */
    @Test
    public void testBegin() {

        BeginRequest request = new BeginRequest(0);

        GenericSuccessResponse result = instance.begin(request);
        assertEquals(true, result.getSuccess());

        result = instance.begin(request);
        assertEquals(false, result.getSuccess());
    }

    /**
     * Test of commit method, of class MVCC2PL.
     */
    @Test
    public void testCommit() {

        try {
            CommitRequest request = new CommitRequest(0);

            GenericSuccessResponse result = instance.commit(request);
            assertEquals(false, result.getSuccess());

            GenericSuccessResponse br;
            br = instance.begin(new BeginRequest(0));
            assertEquals(true, br.getSuccess());

            result = instance.commit(request);
            assertEquals(true, result.getSuccess());

            result = instance.commit(request);
            assertEquals(false, result.getSuccess());

            ReadResponse rr = instance.read(new ReadRequest(0, 0, 0));
            assertEquals(false, rr.getSuccess());

            result = instance.write(new WriteRequest(0, 0, 0, 0));
            assertEquals(false, result.getSuccess());

            // WHAT SHOULD BE RESULT, true or false? At the moment it will
            // succeed and test will fail
            result = instance.begin(new BeginRequest(0));
            assertEquals(true, result.getSuccess());

        } catch (IOException ex) {
            fail(ex.getLocalizedMessage());
        }

    }

    @Test
    public void testSingle() {
        try {

            GenericSuccessResponse br = instance.begin(new BeginRequest(1));
            assertEquals(true, br.getSuccess());

            GenericSuccessResponse wr = instance.write(new WriteRequest(1, 0, "zero", 0));
            assertEquals(true, wr.getSuccess());

            ReadResponse rr = instance.read(new ReadRequest(1, 0, 0));
            assertEquals(true, rr.getSuccess());
            assertEquals("zero", (String) rr.getValue());

            GenericSuccessResponse cr = instance.commit(new CommitRequest(1));
            assertEquals(true, cr.getSuccess());

        } catch (IOException ex) {
            fail(ex.getLocalizedMessage());
        }

    }

    @Test
    public void testSerial() {
        try {

            GenericSuccessResponse gsr = instance.begin(new BeginRequest(0));
            assertEquals(true, gsr.getSuccess());

            gsr = instance.write(new WriteRequest(0, 0, "zero", 0));
            assertEquals(true, gsr.getSuccess());

            gsr = instance.write(new WriteRequest(0, 1, "ONE", 0));
            assertEquals(true, gsr.getSuccess());

            gsr = instance.write(new WriteRequest(0, 1, "one", 0));
            assertEquals(true, gsr.getSuccess());

            gsr = instance.commit(new CommitRequest(0));
            assertEquals(true, gsr.getSuccess());

            gsr = instance.begin(new BeginRequest(1));
            assertEquals(true, gsr.getSuccess());

            ReadResponse rr = instance.read(new ReadRequest(1, 1, 0));
            assertEquals(true, rr.getSuccess());
            assertEquals("one", (String) rr.getValue());

            rr = instance.read(new ReadRequest(1, 0, 0));
            assertEquals(true, rr.getSuccess());
            assertEquals("zero", (String) rr.getValue());

            gsr = instance.commit(new CommitRequest(1));
            assertEquals(true, gsr.getSuccess());
        } catch (IOException ex) {
            fail(ex.getLocalizedMessage());
        }

    }

    public ScheduledCommand BEGIN() {
        return new ScheduledCommand() {

            @Override
            public void perform(int tid, int step) {
                GenericSuccessResponse gsr = instance.begin(new BeginRequest(tid));
                assertEquals(gsr.getSuccess(), true);
            }
        };
    }

    public ScheduledCommand R(final String key, final boolean shouldSucceed, final String expected) {
        return new ScheduledBlockingCommand() {

            @Override
            public void perform(int tid, int step) {
                try {
                    ReadResponse rr = instance.read(new ReadRequest(tid, key, 0));
                    if (shouldSucceed) {
                        assertEquals(true, rr.getSuccess());
                        assertEquals(expected, (String) rr.getValue());
                    } else
                        assertEquals(false, rr.getSuccess());

                } catch (IOException ex) {
                    Logger.getLogger(Simple2PLTest.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        };
    }
}
