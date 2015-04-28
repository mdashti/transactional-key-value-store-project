package ch.epfl.tkvs.yarn;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.ApplicationSubmissionContext;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.client.api.YarnClientApplication;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.util.Records;
import org.apache.log4j.Logger;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import ch.epfl.tkvs.config.NetConfig;
import ch.epfl.tkvs.test.userclient.UserClient;
import ch.epfl.tkvs.transactionmanager.lockingunit.LockingUnitTest;
import ch.epfl.tkvs.transactionmanager.versioningunit.VersioningUnitTest;


public class Client {

    private static Logger log = Logger.getLogger(Client.class.getName());
    private static String AMHostname = null;
    private YarnConfiguration conf;

    public static void main(String[] args) {
        try {
            log.info("Initializing...");
            new Client().run();
        } catch (Exception ex) {
            log.fatal("Could not run yarn client", ex);
        }
    }

    private void run() throws Exception {
        conf = new YarnConfiguration();
        
        NetConfig netConfig = new NetConfig();
        netConfig.cleanUpOldRuns();

        // Create Yarn Client
        YarnClient client = YarnClient.createYarnClient();
        client.init(conf);
        client.start();

        
        // Create Application
        YarnClientApplication app = client.createApplication();
        
        // Create AM Container
        ContainerLaunchContext amCLC = Records.newRecord(ContainerLaunchContext.class);
        amCLC.setCommands(Collections.singletonList("$JAVA_HOME/bin/java " + Utils.AM_XMX + " ch.epfl.tkvs.yarn.appmaster.AppMaster" + " 1>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/stdout" + " 2>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/stderr"));

        // Set AM jar
        LocalResource jar = Records.newRecord(LocalResource.class);
        Utils.setUpLocalResource(Utils.TKVS_JAR_PATH, jar, conf);
        amCLC.setLocalResources(Collections.singletonMap(Utils.TKVS_JAR_NAME, jar));

        // Set AM CLASSPATH
        Map<String, String> env = new HashMap<String, String>();
        Utils.setUpEnv(env, conf);
        amCLC.setEnvironment(env);

        // Set AM resources
        Resource res = Records.newRecord(Resource.class);
        res.setMemory(256);
        res.setVirtualCores(1);

        // Create ApplicationSubmissionContext
        ApplicationSubmissionContext appContext = app.getApplicationSubmissionContext();
        appContext.setApplicationName("TKVS");
        appContext.setQueue("default");
        appContext.setAMContainerSpec(amCLC);
        appContext.setResource(res);
        
        
        // Submit Application
        ApplicationId id = appContext.getApplicationId();
        log.info("Submitting " + id);
        client.submitApplication(appContext);
        
        // Write the id of the app in a hidden file
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(".last_app_id")));
        writer.write(id.toString());
        writer.close();
        
        // The AppMaster will write its host name to HDFS as soon as it is ready
        AMHostname = netConfig.waitForAppMasterHostname();
        if (AMHostname == null) {
        	throw new Exception("no hostname for the AppMaster");
        } else {
        	log.info("YARN client just received the AppMaster hostname: " + AMHostname);
        }
        
        // REPL
        System.out.println("\nClient REPL:");
        Thread.sleep(2000);
        ApplicationReport appReport = client.getApplicationReport(id);
        YarnApplicationState appState = appReport.getYarnApplicationState();
        while (appState != YarnApplicationState.FINISHED && appState != YarnApplicationState.KILLED && appState != YarnApplicationState.FAILED) {

            String input = System.console().readLine("> ");
            if (input.equals(":exit")) {
                log.info("Stopping gracefully " + id);
                Socket exitSock = new Socket(AMHostname, NetConfig.AM_DEFAULT_PORT);
                PrintWriter out = new PrintWriter(exitSock.getOutputStream(), true);
                out.println(input);
                out.close();
                exitSock.close();
                break;
            }

            // TODO: support more commands with more cases ..
            switch (input) {
            case ":test":
                System.out.println("Running test client...\n");

                log.info("Running example client program...");
                new UserClient().run();

                //runTestCases();

                System.out.println();
                break;
            case "":
                break;
            default:
                System.out.println("Command Not Found!");
            }

            appReport = client.getApplicationReport(id);
            appState = appReport.getYarnApplicationState();
        }

        while (appState != YarnApplicationState.FINISHED && appState != YarnApplicationState.KILLED && appState != YarnApplicationState.FAILED) {
            appReport = client.getApplicationReport(id);
            appState = appReport.getYarnApplicationState();
            Thread.sleep(300);
        }
        log.info("Application Stopped");
        client.stop();
    }

    private static void runTestCases() {
        log.info("Running LockingUnitTest... (might take a while)");
        runTestCase(LockingUnitTest.class);

        log.info("Running VersioningUnitTest...");
        runTestCase(VersioningUnitTest.class);
    }

    private static void runTestCase(Class<?> testCase) {
        Result res = JUnitCore.runClasses(testCase);

        if (res.getFailureCount() == 0) {
            log.info("All tests passed for " + testCase.getSimpleName());
        }

        for (Failure failure : res.getFailures()) {
            log.error(failure.toString());
        }
    }
}
