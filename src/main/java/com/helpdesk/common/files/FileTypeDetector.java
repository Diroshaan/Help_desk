package com.helpdesk.common.files;

import java.util.Optional;
import java.util.Set;

/**
 * Shared - decides what kind of file an upload really is, from its own bytes.
 *
 * WHY THIS EXISTS
 * ---------------
 * The NFR (File Attachment Security) says uploads must be validated against
 * strict MIME criteria: PDF and standard images only. The MIME type a browser
 * sends with a multipart upload (MultipartFile.getContentType()) is written by
 * the client. Anyone with curl can label an .exe "image/png", and code that
 * checks only that label stores the .exe and later serves it back to an
 * officer as an image.
 *
 * Every real file format starts with a fixed signature, its "magic bytes".
 * Reading those is the server deciding for itself. F1 did this for avatars
 * (StudentService); F2 (ticket attachments) and F4 (resolution files) need the
 * same check plus PDF. Three private copies of the same byte tables would
 * drift, so the check lives here once, tested once, and every feature calls
 * it.
 *
 * WHAT IT RECOGNISES
 * ------------------
 *   PDF   25 50 44 46 2D                "%PDF-"
 *   PNG   89 50 4E 47 0D 0A 1A 0A
 *   JPEG  FF D8 FF
 *   GIF   47 49 46 38 (37|39) 61        "GIF87a" / "GIF89a"
 *   WEBP  52 49 46 46 .. .. .. .. 57 45 42 50   "RIFF", size, "WEBP"
 *
 * Deliberately NOT recognised: SVG. It is an "image" to a browser, but it is
 * XML that can carry script, so it must never be accepted as an upload.
 *
 * HOW TO USE IT (F2 / F4)
 * -----------------------
 *   String type = FileTypeDetector.detect(bytes, FileTypeDetector.ATTACHMENT_TYPES)
 *           .orElseThrow(() -> new IllegalArgumentException(
 *                   "Only PDF and image files (PNG, JPEG, GIF, WEBP) are allowed."));
 *   attachment.setFileType(type);        // store the DETECTED type, never the client's
 *
 * A utility class of static methods, not a Spring bean: it has no state and no
 * dependencies, so there is nothing to inject and every caller (including a
 * plain unit test) can use it without a Spring context.
 */
public final class FileTypeDetector {

    public static final String PDF = "application/pdf";
    public static final String PNG = "image/png";
    public static final String JPEG = "image/jpeg";
    public static final String GIF = "image/gif";
    public static final String WEBP = "image/webp";

    /** What a ticket attachment or a resolution file may be (NFR: PDF and standard images). */
    public static final Set<String> ATTACHMENT_TYPES = Set.of(PDF, PNG, JPEG, GIF, WEBP);

    /** What a profile picture may be. GIF is left out on purpose: an avatar is a still photo. */
    public static final Set<String> AVATAR_TYPES = Set.of(PNG, JPEG, WEBP);

    private FileTypeDetector() {
        // Static helpers only - never instantiated.
    }

    /**
     * The MIME type these bytes really are, if it is one of {@code allowed}.
     *
     * Empty when the bytes match no known signature, or match one that is not
     * in {@code allowed}. The caller turns empty into a 400 with its own
     * message, because only the caller knows what the user was trying to
     * upload.
     */
    public static Optional<String> detect(byte[] bytes, Set<String> allowed) {
        return detect(bytes).filter(allowed::contains);
    }

    /** The MIME type these bytes really are, or empty if they match no signature we recognise. */
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
        // WEBP is a RIFF container: "RIFF", then four bytes of length, then
        // "WEBP". Checking only "RIFF" would also accept WAV and AVI files.
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
            // & 0xFF because Java bytes are signed: 0x89 is stored as -119.
            if ((bytes[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
