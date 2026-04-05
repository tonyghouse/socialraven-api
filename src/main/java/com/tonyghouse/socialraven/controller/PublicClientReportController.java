package com.tonyghouse.socialraven.controller;

import com.tonyghouse.socialraven.dto.reporting.PublicClientReportResponse;
import com.tonyghouse.socialraven.service.reporting.ClientReportService;
import org.springframework.http.ContentDisposition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/client-reports")
public class PublicClientReportController {

    @Autowired
    private ClientReportService clientReportService;

    @GetMapping("/{token}")
    public ResponseEntity<PublicClientReportResponse> getPublicReport(@PathVariable String token) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, private, max-age=0")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .header("X-Robots-Tag", "noindex, nofollow, noarchive")
                .body(clientReportService.getPublicReport(token));
    }

    @GetMapping(value = "/{token}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadPublicReportPdf(@PathVariable String token) {
        ClientReportService.ClientReportPdfDocument pdf = clientReportService.getPublicReportPdf(token);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, private, max-age=0")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .header("X-Robots-Tag", "noindex, nofollow, noarchive")
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(pdf.fileName()).build().toString()
                )
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf.bytes());
    }
}
