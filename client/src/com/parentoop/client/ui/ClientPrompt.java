package com.parentoop.client.ui;


import com.parentoop.client.masterproxy.MasterMessageHandler;
import com.parentoop.client.masterproxy.MasterProxy;
import com.parentoop.core.networking.Messages;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class ClientPrompt {

    public static final String START_TASK_COMMAND = "run";
    public static final String DEFAULT_OUTPUT_NAME = "out.txt";

    private static String mTaskConfiguratorName;
    private static Path mJarPath;
    private static Path mInputPath;
    private static InetAddress mHostAddress = null;
    private static String mOutputName;

    public static void main(String[] args) throws IOException {
         startUIScript(System.in, System.out, args);
    }

    public static void startUIScript(InputStream inputStream, PrintStream printStream, String[] args) throws IOException {


        Scanner scanner = new Scanner(inputStream).useDelimiter("\\n");
        if(args.length < 4){
            args = new String[5];
            printStream.print("You're in " + Paths.get(".").toAbsolutePath() + "\n");
            printStream.print("Please indicate the relative path to the .jar file containing your MapReduce settings: ");
            args[0] = scanner.next();

            printStream.print("Task configuration class name: ");
            args[1] = scanner.next();

            printStream.print("Master IP address: ");
            args[2] = scanner.next();

            printStream.print("Path to input (on Master machine): ");
            args[3] = scanner.next();

            printStream.print("Output file name (or press Enter to use default name \"" + DEFAULT_OUTPUT_NAME + "\"): ");
            args[4] = scanner.next();
            if(args[4].isEmpty()) args[4] = DEFAULT_OUTPUT_NAME;

        } else {
            if (args.length == 4) {
                mOutputName = DEFAULT_OUTPUT_NAME;
            }
            else {
                mOutputName = args[4];
            }
        }

        mJarPath = Paths.get(args[0]);
        mTaskConfiguratorName = args[1];
        try {
            mHostAddress = InetAddress.getByName(args[2]);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        mInputPath = Paths.get(args[3]);

        printStream.println("Sending " + mJarPath + " to " + mHostAddress + "...");

        printStream.println("Press <enter> at any moment to start the task\n");

        MasterMessageHandler messageHandler = new MasterMessageHandler(printStream, mOutputName);
        MasterProxy masterProxy = new MasterProxy(mHostAddress, messageHandler);
        messageHandler.setProxy(masterProxy);
        masterProxy.sendFile(Messages.LOAD_JAR, mJarPath);

        masterProxy.dispatchMessage(Messages.LOAD_INPUT_PATH, mInputPath.toString());

        // <enter> to start the task
        scanner.nextLine();

        masterProxy.dispatchMessage(Messages.START_TASK, mTaskConfiguratorName);

        printStream.println("-- Task started!");

        // Don't close printStream because there are threads still running that might want to print
        //printStream.close();
        scanner.close();
    }


}
