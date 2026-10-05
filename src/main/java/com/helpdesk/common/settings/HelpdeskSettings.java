package com.helpdesk.common.settings;

import com.helpdesk.common.files.FileTypeDetector;

import java.util.Set;

/**
 * Singleton: the one shared set of system-wide limits, read by F1-F5.
 * Values are the same ones the functions used before, so behaviour is unchanged.
 */
public final class HelpdeskSettings {

    private static volatile HelpdeskSettings instance;

    private final long maxUploadBytes = 5L * 1024 * 1024;   // F2 attachments, F4 resolution files
    private final long maxAvatarBytes = 2L * 1024 * 1024;   // F1 profile pictures
    private final int ticketDefaultPageSize = 20;           // F3 ticket search
    private final int ticketMaxPageSize = 100;              // F3 ticket search
    private final int articleMaxPageSize = 50;              // F5 knowledge base
    private final int maxPage = 10_000;                     // F3 and F5

    private HelpdeskSettings() {
    }

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
}
