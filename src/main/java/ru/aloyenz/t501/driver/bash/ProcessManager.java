package ru.aloyenz.t501.driver.bash;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.aloyenz.t501.driver.config.Configuration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProcessManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessManager.class);

    private static final ExecutorService EXECUTOR = Executors
            .newFixedThreadPool(Configuration.getInstance().maxThreadsForScripts);
    private static final List<Process> RUNNING_PROCESSES = new ArrayList<>();

    public static void runScripts(String[][] scripts) {
        EXECUTOR.execute(() -> {
            for (String[] script : scripts) {

                ProcessBuilder pb = new ProcessBuilder(script);

                Process p = null;

                try {
                    p = pb.start();

                    UUID uuid = UUID.randomUUID();
                    LOGGER.debug("Started process {} for script: {}", uuid, String.join(" ", script));
                    logStream(p.getErrorStream(), "ERR", uuid, true);
                    logStream(p.getInputStream(), "OUT", uuid, false);

                    synchronized (RUNNING_PROCESSES) {
                        RUNNING_PROCESSES.add(p);
                    }

                    int exitCode = p.waitFor();

                    if (exitCode != 0) {
                        LOGGER.error("Process exited with code {}", exitCode);
                    }
                } catch (InterruptedException e) {
                    // Exiting thread immediately
                    return;
                } catch (IOException e) {
                    LOGGER.error("Process exited with exception", e);
                } finally {
                    synchronized (RUNNING_PROCESSES) {
                        if (p != null) {
                            RUNNING_PROCESSES.remove(p);
                        }
                    }
                }
            }
        });
    }

    private static void logStream(InputStream is, String streamName, UUID processId, boolean err) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (err) {
                        LOGGER.error("{} {}: {}", streamName, processId, line);
                    } else {
                        LOGGER.info("{} {}: {}", streamName, processId, line);
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Process exited with exception", e);
            }

        }).start();
    }

    public static void shutdown() {
        EXECUTOR.shutdownNow();

        synchronized (RUNNING_PROCESSES) {
            for (Process p : RUNNING_PROCESSES) {
                p.destroyForcibly();
            }

            RUNNING_PROCESSES.clear();
        }
    }
}
