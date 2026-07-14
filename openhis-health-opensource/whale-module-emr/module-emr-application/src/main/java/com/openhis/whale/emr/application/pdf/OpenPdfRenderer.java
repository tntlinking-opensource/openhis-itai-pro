/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.pdf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfCopy;
import com.lowagie.text.pdf.PdfImportedPage;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.emr.application.pdf.config.PrintConfig;
import com.openhis.whale.emr.application.pdf.model.PdfRenderOptions;
import com.openhis.whale.emr.application.pdf.model.PdfRenderResult;
import com.openhis.whale.emr.application.document.request.GeneratePdfRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/**
 * ProseMirror JSON + PrintConfig → PDF（OpenPDF）。
 */
@Service
@RequiredArgsConstructor
public class OpenPdfRenderer {

    private final PrintConfigResolver printConfigResolver;
    private final EmrContentJsonRenderer contentJsonRenderer;
    private final EmrPdfFontFactory fontFactory;
    private final ObjectMapper objectMapper;

    public PdfRenderResult render(String contentJson, GeneratePdfRequest request) {
        if (!StringUtils.hasText(contentJson)) {
            throw new BusinessException("EMR_PDF_EMPTY", "文档内容为空，无法生成 PDF");
        }
        PrintConfig printConfig = printConfigResolver.resolve(request);
        PdfRenderOptions options = toRenderOptions(request);

        try {
            Rectangle pageSize = buildPageSize(printConfig);
            float marginLeft = PdfUnitUtils.mmToPt(printConfig.getMargins().getLeftMm());
            float marginRight = PdfUnitUtils.mmToPt(printConfig.getMargins().getRightMm());
            float headerHeightMm = 0f;
            if (printConfig.getHeader().isEnabled()) {
                headerHeightMm = printConfig.getHeader().getHeightMm();
            }
            float footerHeightMm = 0f;
            if (printConfig.getFooter().isEnabled()) {
                footerHeightMm = printConfig.getFooter().getHeightMm();
            }
            float marginTop = PdfUnitUtils.mmToPt(printConfig.getMargins().getTopMm())
                    + PdfUnitUtils.mmToPt(headerHeightMm);
            float marginBottom = PdfUnitUtils.mmToPt(printConfig.getMargins().getBottomMm())
                    + PdfUnitUtils.mmToPt(footerHeightMm);

            Document document = new Document(pageSize, marginLeft, marginRight, marginTop, marginBottom);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            writer.setPageEvent(new EmrPdfPageEventHelper(printConfig, options, fontFactory));
            document.open();

            boolean truncated = contentJsonRenderer.render(document, writer, contentJson, printConfig, options);
            document.close();

            byte[] bytes = baos.toByteArray();
            int pageCount = writer.getPageNumber();
            return new PdfRenderResult(bytes, pageCount, truncated);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("EMR_PDF_RENDER_FAILED", "PDF 渲染失败: " + e.getMessage(), e);
        }
    }

    /**
     * 合并多份 PDF 字节流。
     */
    public byte[] mergePdfBytes(List<byte[]> pdfParts) {
        if (pdfParts == null || pdfParts.isEmpty()) {
            return new byte[0];
        }
        if (pdfParts.size() == 1) {
            return pdfParts.get(0);
        }
        try {
            Document document = new Document();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfCopy copy = new PdfCopy(document, baos);
            document.open();
            for (byte[] part : pdfParts) {
                if (part == null || part.length == 0) {
                    continue;
                }
                PdfReader reader = new PdfReader(part);
                int pages = reader.getNumberOfPages();
                for (int i = 1; i <= pages; i++) {
                    PdfImportedPage page = copy.getImportedPage(reader, i);
                    copy.addPage(page);
                }
                reader.close();
            }
            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new BusinessException("EMR_PDF_MERGE_FAILED", "PDF 合并失败: " + e.getMessage(), e);
        }
    }

    public String computeContentHash(String contentJson, PrintConfig config) {
        try {
            String configJson = "";
            if (config != null) {
                configJson = objectMapper.writeValueAsString(config);
            }
            String raw = contentJson + configJson;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return null;
        }
    }

    private static Rectangle buildPageSize(PrintConfig printConfig) {
        float w = PdfUnitUtils.mmToPt(printConfig.getPaper().getWidthMm());
        float h = PdfUnitUtils.mmToPt(printConfig.getPaper().getHeightMm());
        if ("landscape".equalsIgnoreCase(printConfig.getPaper().getOrientation())) {
            return new Rectangle(h, w);
        }
        return new Rectangle(w, h);
    }

    private PdfRenderOptions toRenderOptions(GeneratePdfRequest request) {
        PdfRenderOptions options = new PdfRenderOptions();
        options.setPrintConfig(printConfigResolver.resolve(request));
        if (request == null) {
            return options;
        }
        if (StringUtils.hasText(request.getPageRange())) {
            options.setPageRange(request.getPageRange());
        }
        options.setRegionNames(request.getRegionNames());
        options.setIncludeTrackChanges(Boolean.TRUE.equals(request.getIncludeTrackChanges()));
        options.setPreviewMode(!Boolean.FALSE.equals(request.getPreviewMode()));
        if (request.getPreviewMaxPages() != null && request.getPreviewMaxPages() > 0) {
            options.setPreviewMaxPages(request.getPreviewMaxPages());
        }
        options.setDocumentTitle(request.getDocumentTitle());
        options.setDocumentTypeName(request.getDocumentTypeName());
        if (request.getMetadata() != null) {
            options.setMetadata(request.getMetadata());
        }
        return options;
    }
}
