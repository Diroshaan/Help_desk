package com.helpdesk.queue.dto;

import org.springframework.web.multipart.MultipartFile;

/**
 * F4 - Ticket Resolution & Queue Engine (Weerabaddana)
 *
 * Request body for posting or editing a ticket's resolution
 * (POST/PUT /api/queue/{id}/resolution). Bound from a multipart/form-data
 * request via @ModelAttribute rather than @RequestBody, since it optionally
 * carries a supporting file - the same reason TicketController's attachment
 * upload takes a MultipartFile parameter instead of JSON.
 *
 * responseText is deliberately NOT annotated with @NotBlank/@Valid here:
 * for a @ModelAttribute argument (unlike @RequestBody), a failed @Valid
 * throws BindException, which GlobalExceptionHandler has no mapping for
 * and would surface as an unhandled 500 instead of a clean 400.
 * ResolutionService validates responseText and the attachment manually
 * instead, the same way AttachmentService validates its MultipartFile.
 */
public class ResolutionRequest {

    private String responseText;

    private MultipartFile attachment;

    public String getResponseText() {
        return responseText;
    }
    public void setResponseText(String responseText) {
        this.responseText = responseText;
    }
    public MultipartFile getAttachment() {
        return attachment;
    }
    public void setAttachment(MultipartFile attachment) {
        this.attachment = attachment;
    }
}
