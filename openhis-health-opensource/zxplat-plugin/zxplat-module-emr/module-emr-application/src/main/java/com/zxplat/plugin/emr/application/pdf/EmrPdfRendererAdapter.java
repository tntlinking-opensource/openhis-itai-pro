/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.pdf;

import com.zxplat.plugin.emr.application.document.request.GeneratePdfRequest;
import com.zxplat.plugin.emr.application.pdf.model.PdfRenderResult;
import com.zxplat.plugin.emr.domain.document.access.EmrPdfRenderCommand;
import com.zxplat.plugin.emr.domain.document.access.EmrPdfRenderResult;
import com.zxplat.plugin.emr.domain.document.access.EmrPdfRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmrPdfRendererAdapter implements EmrPdfRenderer {

    private final OpenPdfRenderer openPdfRenderer;

    @Override
    public EmrPdfRenderResult render(String contentJson, EmrPdfRenderCommand command) {
        GeneratePdfRequest request = new GeneratePdfRequest();
        request.setDocumentId(command.getDocumentId());
        request.setContent(command.getContent());
        request.setPreviewMode(command.getPreviewMode());
        request.setPatientId(command.getPatientId());
        request.setEncounterId(command.getEncounterId());
        request.setDocumentTitle(command.getDocumentTitle());
        PdfRenderResult result = openPdfRenderer.render(contentJson, request);
        return new EmrPdfRenderResult(result.getPdfBytes());
    }
}
