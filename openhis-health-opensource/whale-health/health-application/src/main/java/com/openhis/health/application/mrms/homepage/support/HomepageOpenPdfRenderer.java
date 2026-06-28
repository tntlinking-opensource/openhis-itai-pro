/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.mrms.homepage.support;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import com.openhis.health.application.mrms.homepage.dto.HomepageDto;
import com.openhis.health.application.mrms.homepage.dto.PdfGenerateResultDto;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Base64;

/**
 * 病案首页 OpenPDF 简化渲染器（homepage IF {@code support}，Wave 1 打印预览）。
 *
 * <p>当前仅输出核心标量字段单页 A4；完整版式待后续模板迭代。</p>
 */
@Component
public class HomepageOpenPdfRenderer {

    /**
     * 将首页 DTO 渲染为 PDF Base64。
     *
     * @param homepage 首页数据
     * @return PDF 结果
     */
    public PdfGenerateResultDto render(HomepageDto homepage) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        try {
            PdfWriter.getInstance(document, buffer);
            document.open();
            Font titleFont = buildFont(16);
            Font bodyFont = buildFont(11);
            document.add(new Paragraph("住院病案首页", titleFont));
            document.add(new Paragraph(" ", bodyFont));
            document.add(new Paragraph("姓名：" + nullToEmpty(homepage.getPsnName()), bodyFont));
            document.add(new Paragraph("住院号：" + nullToEmpty(homepage.getIptNo()), bodyFont));
            document.add(new Paragraph("性别：" + nullToEmpty(homepage.getGendCode()), bodyFont));
            document.add(new Paragraph("入院科别：" + nullToEmpty(homepage.getAdmCatyName()), bodyFont));
            document.add(new Paragraph("出院科别：" + nullToEmpty(homepage.getDscgCatyName()), bodyFont));
            document.add(new Paragraph("主治医师：" + nullToEmpty(homepage.getAtddrName()), bodyFont));
            document.add(new Paragraph("住院医师：" + nullToEmpty(homepage.getIptDrName()), bodyFont));
            document.close();
        } catch (DocumentException ex) {
            throw new IllegalStateException("首页 PDF 渲染失败", ex);
        }
        PdfGenerateResultDto result = new PdfGenerateResultDto();
        result.setPdfBase64(Base64.getEncoder().encodeToString(buffer.toByteArray()));
        result.setPageCount(1);
        result.setRenderedAt(Instant.now());
        return result;
    }

    /**
     * 构建中文字体；STSong 不可用时回退 Helvetica。
     *
     * @param size 字号
     * @return OpenPDF Font
     */
    private Font buildFont(int size) {
        try {
            BaseFont baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            return new Font(baseFont, size);
        } catch (Exception ex) {
            // STSong 不可用时回退 Helvetica，保证预览可用
            return new Font(Font.HELVETICA, size);
        }
    }

    /**
     * null 安全转空串（PDF 段落展示用）。
     *
     * @param value 字符串
     * @return 非 null 字符串
     */
    private String nullToEmpty(String value) {
        if (value == null) {
            return "";
        }
        return value;
    }
}
