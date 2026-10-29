package com.atom.chat.diagnostics;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class FrameProfileJfrTest {
    @Test void recordingContainsTheActualPopupLabelAndRenderDuration(@TempDir Path dir) throws Exception {
        FrameProfile.reset(); FrameProfile.setEnabled(true);
        try (var recording = new Recording()) {
            recording.enable("com.atomchat.UiFrame").withThreshold(Duration.ZERO);
            recording.start();
            FrameProfile.surface(FrameProfile.Surface.EMOJI);
            FrameProfile.frame(System.nanoTime() - 1_000_000, -1);
            FrameProfile.frame(System.nanoTime() - 1_000_000, -1);
            recording.stop(); var file = dir.resolve("popup.jfr"); recording.dump(file);
            var events = RecordingFile.readAllEvents(file).stream()
                    .filter(e -> e.getEventType().getName().equals("com.atomchat.UiFrame")).toList();
            assertEquals(2, events.size());
            assertEquals("EMOJI", events.get(0).getString("surface"));
            assertEquals("OTHER", events.get(1).getString("surface"), "unlabelled HUD draws cannot inherit an old popup");
            assertTrue(events.get(0).getLong("renderNanos") > 0);
        } finally { FrameProfile.setEnabled(false); FrameProfile.reset(); }
    }
}
