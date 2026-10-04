package com.helpdesk.common.files;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** The magic-byte file type check, using real file signatures. */
class FileTypeDetectorTest {

    private static byte[] bytes(int... values) {
        byte[] out = new byte[Math.max(values.length, 16)];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    @Test
    @DisplayName("Recognises PDF, PNG, JPEG, GIF and WEBP from their first bytes")
    void recognisesEveryAcceptedFormat() {
        assertThat(FileTypeDetector.detect("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII)))
                .contains(FileTypeDetector.PDF);
        assertThat(FileTypeDetector.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
                .contains(FileTypeDetector.PNG);
        assertThat(FileTypeDetector.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0)))
                .contains(FileTypeDetector.JPEG);
        assertThat(FileTypeDetector.detect("GIF89a....".getBytes(StandardCharsets.US_ASCII)))
                .contains(FileTypeDetector.GIF);
        assertThat(FileTypeDetector.detect("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII)))
                .contains(FileTypeDetector.WEBP);
    }

    @Test
    @DisplayName("Text renamed to .pdf, an SVG, a WAV file and an .exe are all refused")
    void refusesLookalikes() {
        assertThat(FileTypeDetector.detect("just some notes".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(FileTypeDetector.detect("<svg xmlns=\"http://www.w3.org/2000/svg\">"
                .getBytes(StandardCharsets.US_ASCII))).isEmpty();
        // RIFF alone is not enough - WAV and AVI are RIFF files too.
        assertThat(FileTypeDetector.detect("RIFF\0\0\0\0WAVEfmt ".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(FileTypeDetector.detect(bytes('M', 'Z', 0x90, 0x00))).isEmpty();
    }

    @Test
    @DisplayName("A real format outside the caller's allow-list is refused")
    void honoursTheAllowList() {
        byte[] gif = "GIF89a....".getBytes(StandardCharsets.US_ASCII);
        assertThat(FileTypeDetector.detect(gif, FileTypeDetector.ATTACHMENT_TYPES)).contains(FileTypeDetector.GIF);
        // Avatars are still photos only.
        assertThat(FileTypeDetector.detect(gif, FileTypeDetector.AVATAR_TYPES)).isEmpty();
    }

    @Test
    @DisplayName("Empty, null and truncated input never throws")
    void shortInputIsSafe() {
        assertThat(FileTypeDetector.detect(null)).isEmpty();
        assertThat(FileTypeDetector.detect(new byte[0])).isEmpty();
        assertThat(FileTypeDetector.detect(new byte[] {(byte) 0x89, 0x50})).isEmpty();
        // Starts like PNG but stops before the signature ends.
        assertThat(FileTypeDetector.detect(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D})).isEmpty();
    }
}
