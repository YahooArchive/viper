/*
 * Copyright 2016, Yahoo Inc.
 * Copyrights licensed under the New BSD License.
 * See the accompanying LICENSE file for terms.
 */

package com.yahoo.viper.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Implements just enough of an HTTP server to test the viper functionality.
 */
public class MockServer extends Thread {

    final static Logger logger = LoggerFactory.getLogger(MockServer.class);

    /**
     * Represents the different states that the mock server can be in.
     */
    public enum Mode {
        /**
         * The server handles all requests normally.
         */
        UP,

        /**
         * The server will hang all HTTP requests.
         */
        HANG,

        /**
         * The server will return a 500 error.
         */
        ERROR,

        /**
         * The server will no longer listen to the port.
         */
        DOWN
    }

    private int port;
    private Mode mode = Mode.UP;
    private boolean run = true;
    private ServerSocket serverSocket = null;

    // The run count is used to ensure that the run loop is executing the latest mode.
    private volatile int runCount = 0;

    public MockServer(int port) {
        this.port = port;
    }

    public void close() {
        run = false;
        interruptRun();
    }

    /**
     * Interrupts the thread and waits until the thread has restarted with the new mode.
     *
     * @param mode server mode
     */
    public void setMode(Mode mode) {
        this.mode = mode;
        int curRunCount = runCount;

        while (curRunCount >= runCount) {
            // The only way to interrupt the accept() is to close the server socket
            interruptRun();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    void interruptRun() {
        try {
            interrupt();

            // The only way to interrupt the accept() is to close the server socket
            ServerSocket s = serverSocket;
            serverSocket = null;
            if (s != null) {
                s.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public Mode getMode() {
        return mode;
    }

    private Socket accept() throws IOException {
        if (serverSocket == null) {
            serverSocket = new ServerSocket(port);
        }
        return serverSocket.accept();
    }

    public void run() {
        while (run) {
            Socket client = null;
            InputStream in = null;
            PrintWriter out = null;

            try {
                runCount++;
                switch (mode) {
                    case UP:
                        client = accept();

                        in = client.getInputStream();
                        out = new PrintWriter(client.getOutputStream());

                        out.print("HTTP/1.1 200 \r\n");
                        out.print("Content-Type: text/plain\r\n");
                        out.print("Connection: close\r\n");
                        out.print("\r\n");
                        break;
                    case ERROR:
                        client = accept();
                        in = client.getInputStream();
                        out = new PrintWriter(client.getOutputStream());

                        out.print("HTTP/1.1 500 \r\n");
                        out.print("Content-Type: text/plain\r\n");
                        out.print("Connection: close\r\n");
                        out.print("\r\n");
                        break;
                    case DOWN:
                        if (serverSocket != null) {
                            serverSocket.close();
                            serverSocket = null;
                        }

                        // Avoid spin loop
                        Thread.sleep(1000);
                        break;
                    case HANG:
                        client = accept();
                        in = client.getInputStream();
                        out = new PrintWriter(client.getOutputStream());

                        // Clear and then wait for interrupt
                        Thread.interrupted();
                        Thread.currentThread().join();
                        break;
                }
            } catch (InterruptedException e) {
                // do nothing
            } catch (Exception e) {
                if (!isInterrupted()) {
                    // The interrupted flag indicates that the mode has change and an error should not be printed
                    logger.error(e.getMessage());
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e1) {
                        // Avoid spin loop
                    }
                }
            } finally {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        logger.error(e.getMessage());
                    }
                }
                if (client != null) {
                    try {
                        client.close();
                    } catch (IOException e) {
                        logger.error(e.getMessage());
                    }
                }
            }
        }
    }
}
