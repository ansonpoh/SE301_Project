package org.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SVFRMSTest {

    /** "java -jar run.jar demo" needs no demo folder next to the jar: the layout ships inside it. */
    @Test
    void demoLayoutIsBuiltIn() {
        Path demo = SVFRMS.builtInDemoFolder();
        assertTrue(Files.isRegularFile(demo.resolve("chambers.csv")));
        assertTrue(Files.isRegularFile(demo.resolve("sensors.csv")));
    }

    /** Tests run from target/classes, so this covers the jar: branch that run.jar actually takes. */
    @Test
    void demoLayoutIsReadFromInsideAJar(@TempDir Path tmp) throws Exception {
        Path jarFile = tmp.resolve("app.jar");
        try (OutputStream out = Files.newOutputStream(jarFile); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("demo/chambers.csv"));
            zip.write("CH1, Tomato\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        URI uri = URI.create("jar:" + jarFile.toUri() + "!/demo/chambers.csv");

        Path demo = SVFRMS.demoFolderOf(uri.toURL());
        try {
            assertEquals("CH1, Tomato\n", Files.readString(demo.resolve("chambers.csv")));
            assertEquals(demo, SVFRMS.demoFolderOf(uri.toURL()));   // a second call reuses the open file system
        } finally {
            demo.getFileSystem().close();   // else Windows keeps app.jar locked and @TempDir cannot delete it
        }
    }

    /** A wrong command line is a usage error (exit 2), thrown rather than exiting so it can be checked here. */
    @Test
    void badCommandLinesAreUsageErrors(@TempDir Path tmp) {
        assertThrows(SVFRMS.UsageException.class, () -> SVFRMS.configDir(new String[] {"a", "b"}));
        assertThrows(SVFRMS.UsageException.class,
                () -> SVFRMS.configDir(new String[] {tmp.resolve("missing").toString()}));
        assertThrows(SVFRMS.UsageException.class, () -> SVFRMS.configDir(new String[] {tmp.toString()}));   // no CSV files
        assertThrows(SVFRMS.UsageException.class, () -> SVFRMS.configDir(new String[] {"bad\0name"}));   // not a path
    }

    @Test
    void goodCommandLinesGiveTheConfigFolder(@TempDir Path tmp) throws Exception {
        assertNull(SVFRMS.configDir(new String[0]));
        Files.writeString(tmp.resolve("crops.csv"), "");
        assertEquals(tmp, SVFRMS.configDir(new String[] {tmp.toString()}));
    }

    /** A resource URL with no file system is a broken build (exit 3), not a bad config folder (exit 2). */
    @Test
    void unreadableDemoLocationIsAnInternalError() throws Exception {
        URL url = URI.create("http://example.invalid/demo/chambers.csv").toURL();
        assertThrows(IllegalStateException.class, () -> SVFRMS.demoFolderOf(url));
    }
}
