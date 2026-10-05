package com.helpdesk.common.settings;

import com.helpdesk.common.files.FileTypeDetector;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Singleton: the one shared set of system-wide limits used by F1 to F5.
 *
 * The values are read ONCE from application.properties (file I/O), which is why there is a
 * single shared instance: every function reads the same loaded settings instead of each class
 * keeping its own hard-coded copy. The upload limit comes from the same key Spring uses for
 * multipart uploads (spring.servlet.multipart.max-file-size), so there is one source of truth.
 */
public final class HelpdeskSettings {

    static final String FILE = "/application.properties";

    //static HelpdeskSettings instance variable
    private static volatile HelpdeskSettings instance;

    private final long maxUploadBytes;        // F2 attachments, F4 resolution files
    private final long maxAvatarBytes;        // F1 profile pictures
    private final int ticketDefaultPageSize;  // F3 student ticket search
    private final int ticketMaxPageSize;      // F3 student ticket search
    private final int articleMaxPageSize;     // F5 knowledge base
    private final int maxPage;                // F3 and F5 paging

    //Constructor is private preventing other classes from making new instances
    private HelpdeskSettings() {
        Properties props = new Properties();
        try (InputStream in = HelpdeskSettings.class.getResourceAsStream(FILE)) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + FILE, e);
        }
        maxUploadBytes = parseSize(props.getProperty("spring.servlet.multipart.max-file-size", "5MB"));
        maxAvatarBytes = parseSize(props.getProperty("helpdesk.limits.avatar-max-size", "2MB"));
        ticketDefaultPageSize = intValue(props, "helpdesk.limits.ticket-default-page-size", 20);
        ticketMaxPageSize = intValue(props, "helpdesk.limits.ticket-max-page-size", 100);
        articleMaxPageSize = intValue(props, "helpdesk.limits.article-max-page-size", 50);
        maxPage = intValue(props, "helpdesk.limits.max-page", 10_000);
    }

    //Static method to get instance
    public static HelpdeskSettings getInstance() {
        if (instance == null) {
            synchronized (HelpdeskSettings.class) {
                if (instance == null) {
                    instance = new HelpdeskSettings();
                }
            }
        }
        return instance;
    }

    public long getMaxUploadBytes() { return maxUploadBytes; }
    public long getMaxAvatarBytes() { return maxAvatarBytes; }
    public int getTicketDefaultPageSize() { return ticketDefaultPageSize; }
    public int getTicketMaxPageSize() { return ticketMaxPageSize; }
    public int getArticleMaxPageSize() { return articleMaxPageSize; }
    public int getMaxPage() { return maxPage; }
    public Set<String> getAttachmentTypes() { return FileTypeDetector.ATTACHMENT_TYPES; }
    public Set<String> getAvatarTypes() { return FileTypeDetector.AVATAR_TYPES; }

    // "5MB", "512KB" or a plain number of bytes, as in Spring's multipart settings
    static long parseSize(String value) {
        String v = value.trim().toUpperCase(Locale.ROOT);
        if (v.endsWith("MB")) {
            return Long.parseLong(v.substring(0, v.length() - 2).trim()) * 1024 * 1024;
        }
        if (v.endsWith("KB")) {
            return Long.parseLong(v.substring(0, v.length() - 2).trim()) * 1024;
        }
        if (v.endsWith("B")) {
            v = v.substring(0, v.length() - 1).trim();
        }
        return Long.parseLong(v);
    }

    private static int intValue(Properties props, String key, int fallback) {
        String v = props.getProperty(key);
        return v == null ? fallback : Integer.parseInt(v.trim());
    }
}
