package me.duncanruns.e4mcbiat;

import me.duncanruns.e4mcbiat.gui.E4mcBiatGUI;

import java.io.IOException;
import java.util.Arrays;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws IOException {
        Arrays.stream(args).filter(s -> s.startsWith("port=")).forEach(s -> E4mcClient.defaultPort = Integer.parseInt(s.split("=")[1]));
        if (Arrays.stream(args).anyMatch(s -> s.toLowerCase().contains("nogui"))) {
            runWithNoGUI();
        } else {
            E4mcBiatGUI.main(args);
        }
    }

    private static void runWithNoGUI() throws IOException {
        E4mcClient e4mcClient = new E4mcClient(d -> System.out.println("Connected, domain: " + d), m -> System.out.println("e4mc broadcast: " + m));
        Thread inputThread = new Thread(() -> {
            try {
                Scanner scanner = new Scanner(System.in);
                while (scanner.hasNextLine()) {
                    if (scanner.nextLine().trim().equalsIgnoreCase("stop")) {
                        e4mcClient.close();
                        return;
                    } else {
                        System.out.println("Unknown command.");
                    }
                }
            } catch (Exception e) {
                e4mcClient.close();
            }
        });
        inputThread.setDaemon(true);
        inputThread.start();
        e4mcClient.run();
    }
}
