/*
 * Copyright 2016, Yahoo Inc.
 * Copyrights licensed under the New BSD License.
 * See the accompanying LICENSE file for terms.
 */

package com.yahoo.viper.cli;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

/**
 * This class implements the "server" command line tool. See the README for usage information.
 */
public class Server {

    /**
     * The main entry point for the server command line tool.
     *
     * @param args non-null array of arguments passed to the tool
     * @throws InterruptedException
     */
    public static void main(String[] args) throws InterruptedException {
        Map<Integer, MockServer> servers = new HashMap<>();

        if (args.length < 0) {
            System.out.println("Usage: server <port> [<port>]*");
            return;
        }

        Scanner console = new Scanner(System.in);
        printCommands();
        System.out.println();
        while (console.hasNextLine()) {
            String input = console.nextLine();
            args = input.trim().split("\\s+");
            if (args.length < 1 || !args[0].matches("\\d+")) {
                printCommands();
                continue;
            }

            // Create a server that listens to the specified port
            int port = Integer.parseInt(args[0]);
            MockServer ms = servers.get(port);
            if (ms == null) {
                ms = new MockServer(Integer.parseInt(args[0]));
                ms.start();
                servers.put(port, ms);
            }

            // Set the mode if specified
            MockServer.Mode mode = MockServer.Mode.UP;
            if (args.length > 1) {
                switch (args[1]) {
                    case "down":
                        mode = MockServer.Mode.DOWN;
                        break;
                    case "hang":
                        mode = MockServer.Mode.HANG;
                        break;
                    case "error":
                        mode = MockServer.Mode.ERROR;
                        break;
                }
            }
            ms.setMode(mode);

            System.out.println("Server status:");
            for (Map.Entry<Integer, MockServer> e : servers.entrySet()) {
                System.out.printf("  %d %s\n", e.getKey(),
                                  e.getValue().getMode().toString().toLowerCase(Locale.ENGLISH));
            }
        }
    }

    public static void printCommands() {
        System.out.println("Commands:");
        System.out.println("  <port> [up] - handle requests to the port");
        System.out.println("  <port> down - stop handling requests to the port");
        System.out.println("  <port> hang - hang requests to the port");
        System.out.println("  <port> error - fail requests to the port");
    }
}
