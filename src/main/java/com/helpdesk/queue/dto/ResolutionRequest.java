package com.helpdesk.queue.dto;

import org.springframework.web.multipart.MultipartFile;

/**
 * Multipart form for posting or editing a resolution (it can carry a file).
 * No @NotBlank here: ResolutionService checks the text itself, because a failed
 * @Valid on a form object would come back as a 500 rather than a 400.
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
