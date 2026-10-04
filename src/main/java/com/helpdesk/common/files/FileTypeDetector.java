package com.helpdesk.common.files;

import java.util.Optional;
import java.util.Set;

/**
 * Works out an upload's real type from its first bytes, since the client's Content-Type
 * can be faked. Shared by avatars, ticket attachments and resolution files.
 *   PDF 25 50 44 46 2D ("%PDF-"), PNG 89 50 4E 47 0D 0A 1A 0A, JPEG FF D8 FF,
 *   GIF "GIF87a"/"GIF89a", WEBP "RIFF" + 4 size bytes + "WEBP".
 * SVG is never accepted: it is XML and can contain script.
 */
public final class FileTypeDetector {

    public static final String PDF = "application/pdf";
    public static final String PNG = "image/png";
    public static final String JPEG = "image/jpeg";
    public static final String GIF = "image/gif";
    public static final String WEBP = "image/webp";

    // Ticket attachments and resolution files (NFR: PDF and standard images).
    public static final Set<String> ATTACHMENT_TYPES = Set.of(PDF, PNG, JPEG, GIF, WEBP);

    // No GIF for avatars - a profile picture should be a still image.
    public static final Set<String> AVATAR_TYPES = Set.of(PNG, JPEG, WEBP);

    private FileTypeDetector() {
        // static helpers only
    }

    // Empty if the type isn't recognised or isn't allowed; the caller turns that into a 400.
    public static Optional<String> detect(byte[] bytes, Set<String> allowed) {
        return detect(bytes).filter(allowed::contains);
    }

    public static Optional<String> detect(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return Optional.empty();
        }
        if (startsWith(bytes, 0x25, 0x50, 0x44, 0x46, 0x2D)) {
            return Optional.of(PDF);
        }
        if (startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(bytes, 0x47, 0x49, 0x46, 0x38, 0x37, 0x61)
                || startsWith(bytes, 0x47, 0x49, 0x46, 0x38, 0x39, 0x61)) {
            return Optional.of(GIF);
        }
        // "RIFF" alone would also match WAV and AVI, so check for "WEBP" at byte 8 too.
        if (bytes.length >= 12
                && startsWith(bytes, 0x52, 0x49, 0x46, 0x46)
                && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            // & 0xFF because Java bytes are signed
            if ((bytes[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
