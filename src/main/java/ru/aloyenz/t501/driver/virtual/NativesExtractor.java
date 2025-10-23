package ru.aloyenz.t501.driver.virtual;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.aloyenz.t501.driver.DriverMain;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class NativesExtractor {

    private static final Logger logger = LoggerFactory.getLogger(NativesExtractor.class);

    public static void extract() throws IOException {
        replaceLibIfNeeded("libvpen");
        replaceLibIfNeeded("libvmouse");
        replaceLibIfNeeded("libvkeyboard");
    }

    private static void replaceLibIfNeeded(String libName) throws IOException {
        String resourcePath = getResourceLibPath(libName);

        String resourceHash = getResourceFileHash(resourcePath);
        String outputHash;
        try {
            outputHash = getOutputLibHash(libName);
        } catch (RuntimeException e) {
            // File not found or error reading, extract the library
            logger.warn("Native library '{}' is missing. Extracting it.", libName);
            extractLibrary(libName);
            return;
        }

        if (!resourceHash.equals(outputHash)) {
            // Replacing
            logger.warn("Native library '{}' is outdated or missing. Replacing it.", libName);
            extractLibrary(libName);
        }
    }

    private static String getResourceLibPath(String libName) {
        return "native-build/ru/aloyenz/t501/driver/virtual/" + libName + ".so";
    }

    private static String getOutputLibPath(String libName) {
        return DriverMain.getNativePathPrefix() + libName + ".so";
    }

    private static String getOutputLibHash(String libName) {
        String outputPath = getOutputLibPath(libName);

        try (InputStream in = Files.newInputStream(Paths.get(outputPath))) {
            // Calculate hash
            return calculateHash(in);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate hash for output file: " + outputPath, e);
        }
    }

    private static String getResourceFileHash(String path) {
        try (InputStream in = NativesExtractor.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new FileNotFoundException("Resource not found: " + path);
            }

            // Calculate hash
            return calculateHash(in);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate hash for resource: " + path, e);
        }
    }

    private static String calculateHash(InputStream in) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            digest.update(buffer, 0, bytesRead);
        }
        byte[] hashBytes = digest.digest();

        // Convert to hex string
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static void extractLibrary(String libName) throws IOException {
        String resourcesPath = getResourceLibPath(libName);
        String outputPath = getOutputLibPath(libName);

        // Reading the library from resources
        try (InputStream in = NativesExtractor.class.getClassLoader().getResourceAsStream(resourcesPath)) {
            if (in == null) {
                throw new FileNotFoundException("Resource not found: " + resourcesPath);
            }

            // Writing all bytes to the output file
            Files.copy(in, java.nio.file.Paths.get(outputPath), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
