package org.rhapsodychecker.rhapsodyruleverifier.prefs;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class RhapsodyPathSetupTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    // ── isValidJavaApiPath ──────────────────────────────────────────────────

    @Test
    public void nullPath_isInvalid() {
        assertFalse(RhapsodyPathSetup.isValidJavaApiPath(null));
    }

    @Test
    public void emptyPath_isInvalid() {
        assertFalse(RhapsodyPathSetup.isValidJavaApiPath(""));
    }

    @Test
    public void nonExistentDirectory_isInvalid() {
        assertFalse(RhapsodyPathSetup.isValidJavaApiPath("Z:\\nonexistent\\path"));
    }

    @Test
    public void directoryWithoutMarker_isInvalid() {
        assertFalse(RhapsodyPathSetup.isValidJavaApiPath(tempDir.getRoot().getAbsolutePath()));
    }

    @Test
    public void directoryWithMarker_isValid() throws IOException {
        tempDir.newFile("rhapsody.jar");
        assertTrue(RhapsodyPathSetup.isValidJavaApiPath(tempDir.getRoot().getAbsolutePath()));
    }

    @Test
    public void multiSegmentPath_validSegmentFound() throws IOException {
        File validDir = tempDir.newFolder("valid");
        new File(validDir, "rhapsody.jar").createNewFile();

        String multiPath = "C:\\nonexistent;" + validDir.getAbsolutePath() + ";D:\\also\\fake";
        assertTrue(RhapsodyPathSetup.isValidJavaApiPath(multiPath));
    }

    @Test
    public void multiSegmentPath_noValidSegment() {
        String multiPath = "C:\\nonexistent;D:\\also\\fake";
        assertFalse(RhapsodyPathSetup.isValidJavaApiPath(multiPath));
    }

    // ── updateIniFile ───────────────────────────────────────────────────────

    @Test
    public void updateIniFile_replacesExistingLine() throws IOException {
        Path ini = tempDir.getRoot().toPath().resolve("test.l4j.ini");
        List<String> original = Arrays.asList(
                "# comment",
                "-Djava.library.path=\"C:\\old\\path\"",
                "-Xmx512m"
        );
        Files.write(ini, original, StandardCharsets.UTF_8);

        boolean result = RhapsodyPathSetup.updateIniFile("C:\\new\\path", ini);

        assertTrue(result);
        List<String> lines = Files.readAllLines(ini, StandardCharsets.UTF_8);
        assertEquals(3, lines.size());
        assertEquals("# comment", lines.get(0));
        assertEquals("-Djava.library.path=\"C:\\new\\path\"", lines.get(1));
        assertEquals("-Xmx512m", lines.get(2));
    }

    @Test
    public void updateIniFile_addsLineWhenMissing() throws IOException {
        Path ini = tempDir.getRoot().toPath().resolve("test.l4j.ini");
        List<String> original = Arrays.asList("# comment", "-Xmx512m");
        Files.write(ini, original, StandardCharsets.UTF_8);

        boolean result = RhapsodyPathSetup.updateIniFile("C:\\new\\path", ini);

        assertTrue(result);
        List<String> lines = Files.readAllLines(ini, StandardCharsets.UTF_8);
        assertEquals(3, lines.size());
        assertEquals("-Djava.library.path=\"C:\\new\\path\"", lines.get(2));
    }

    @Test
    public void updateIniFile_nullPath_returnsFalse() {
        assertFalse(RhapsodyPathSetup.updateIniFile("C:\\whatever", (Path) null));
    }

    @Test
    public void updateIniFile_preservesOtherLines() throws IOException {
        Path ini = tempDir.getRoot().toPath().resolve("test.l4j.ini");
        List<String> original = Arrays.asList(
                "# Rhapsody Rule Verifier",
                "-Djava.library.path=\"C:\\old\"",
                "-Dsomething.else=true",
                "# end"
        );
        Files.write(ini, original, StandardCharsets.UTF_8);

        RhapsodyPathSetup.updateIniFile("D:\\Rhapsody\\Share\\JavaAPI", ini);

        List<String> lines = Files.readAllLines(ini, StandardCharsets.UTF_8);
        assertEquals(4, lines.size());
        assertEquals("# Rhapsody Rule Verifier", lines.get(0));
        assertEquals("-Djava.library.path=\"D:\\Rhapsody\\Share\\JavaAPI\"", lines.get(1));
        assertEquals("-Dsomething.else=true", lines.get(2));
        assertEquals("# end", lines.get(3));
    }
}