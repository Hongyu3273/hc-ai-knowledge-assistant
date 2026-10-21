package com.hc.aiagenthyc.controller;

import com.hc.aiagenthyc.attachment.model.StoredAttachment;
import com.hc.aiagenthyc.attachment.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/attachments")
public class AttachmentController {
    private final AttachmentService service;
    public AttachmentController(AttachmentService service) { this.service = service; }
    @Operation(summary = "Upload a chat image", description = "Supports JPEG, PNG, and WebP; returns a fileId for use in chat requests")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public StoredAttachment upload(@RequestPart("file") MultipartFile file) { return service.upload(file); }
}
