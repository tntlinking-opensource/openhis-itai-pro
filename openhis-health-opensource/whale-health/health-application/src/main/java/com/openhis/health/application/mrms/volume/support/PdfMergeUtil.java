/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.volume.support;

import com.lowagie.text.Document;
import com.lowagie.text.pdf.PdfCopy;
import com.lowagie.text.pdf.PdfImportedPage;
import com.lowagie.text.pdf.PdfReader;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * OpenPDF 多段 PDF 合并与页数统计（volume IF {@code support}）。
 */
@Component
public class PdfMergeUtil {

    /**
     * 按顺序合并多份 PDF 字节流；空段跳过，单段直接返回。
     *
     * @param pdfParts PDF 字节段列表，可为 null
     * @return 合并结果；无有效段时返回空数组
     */
    public byte[] mergePdfBytes(List<byte[]> pdfParts) {
        // 无输入段时返回空数组
        if (pdfParts == null || pdfParts.isEmpty()) {
            return new byte[0];
        }
        List<byte[]> valid = new ArrayList<>();
        for (byte[] part : pdfParts) {
            if (part != null && part.length > 0) {
                valid.add(part);
            }
        }
        if (valid.isEmpty()) {
            return new byte[0];
        }
        // 单段无需合并，直接返回
        if (valid.size() == 1) {
            return valid.get(0);
        }
        try {
            Document document = new Document();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            PdfCopy copy = new PdfCopy(document, buffer);
            document.open();
            for (byte[] part : valid) {
                PdfReader reader = new PdfReader(part);
                int pages = reader.getNumberOfPages();
                for (int page = 1; page <= pages; page++) {
                    PdfImportedPage imported = copy.getImportedPage(reader, page);
                    copy.addPage(imported);
                }
                reader.close();
            }
            document.close();
            return buffer.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("卷宗 PDF 合并失败", ex);
        }
    }

    /**
     * 统计 PDF 页数；解析失败时返回 0。
     *
     * @param pdfBytes PDF 字节流
     * @return 页数
     */
    public int countPages(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return 0;
        }
        try {
            PdfReader reader = new PdfReader(pdfBytes);
            int pages = reader.getNumberOfPages();
            reader.close();
            return pages;
        } catch (Exception ex) {
            return 0;
        }
    }

    /**
     * Base64 解码 PDF 段；空白输入返回空数组。
     *
     * @param pdfBase64 Base64 编码 PDF
     * @return 解码字节流
     */
    public byte[] decodeBase64(String pdfBase64) {
        if (pdfBase64 == null || pdfBase64.isBlank()) {
            return new byte[0];
        }
        return Base64.getDecoder().decode(pdfBase64);
    }
}
