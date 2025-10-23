import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.aloyenz.t501.driver.device.KeyCode;
import ru.aloyenz.t501.driver.device.KeyCodesFetcher;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class KeyCodesFetcherTest {

    private static final Logger log = LoggerFactory.getLogger(KeyCodesFetcherTest.class);

    @Test
    public void testKeyCodesFetcher() throws IOException {
        KeyCodesFetcher.load("keycodes.json", null, false);

        // Showing all key codes
        for (Map.Entry<String, KeyCode> codeEntry : KeyCodesFetcher.getKeycodes().entrySet()) {
            log.info("Code name: {}, code: {}, comment: {}", codeEntry.getKey(),
                    codeEntry.getValue().keyCode(), codeEntry.getValue().comment());
        }

        // Storing to the file
        KeyCodesFetcher.storeKeycodesToFile(new File("keycodes_output.json"));
    }

    @Test
    public void testCacheExtract() {
        String keycodesCache = "keycodes_cache.json";

        KeyCodesFetcher.saveKeycodeCacheFromResources(keycodesCache);
        try {
            KeyCodesFetcher.loadKeycodesFromCache(keycodesCache);
        } catch (IOException e) {
            log.error("Could not load keycodes from predefined cache: {}", e.getMessage(), e);
            throw new RuntimeException("Could not load keycodes from predefined cache: " + e.getMessage());
        }
    }
}
