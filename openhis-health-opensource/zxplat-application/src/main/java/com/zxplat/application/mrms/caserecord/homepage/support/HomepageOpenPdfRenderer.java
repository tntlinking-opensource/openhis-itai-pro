/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.caserecord.homepage.support;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import com.zxplat.application.mrms.caserecord.homepage.dto.DiagnosisLineDto;
import com.zxplat.application.mrms.caserecord.homepage.dto.HomepageDto;
import com.zxplat.application.mrms.caserecord.homepage.dto.PdfGenerateResultDto;
import com.zxplat.application.mrms.caserecord.homepage.dto.ProcedureLineDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * 病案首页 OpenPDF 简化渲染器（homepage IF {@code support}，卷宗/查档预览）。
 *
 * <p>打印口径用<strong>国临码</strong>（落库 {@code diagCode}/{@code oprnOprtCode}）；完整版式待模板迭代。
 * 中医信息段仅在 {@code tcmInfoEnabled=true}（医院 {@code medins_type}）且正式列有值时输出。</p>
 */
@Component
@RequiredArgsConstructor
public class HomepageOpenPdfRenderer {

    private final HospitalTcmInfoEnabledResolver hospitalTcmInfoEnabledResolver;

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
            // 中医信息：仅中医院（medins_type）且正式列有值时打印
            this.appendTcmInfoSection(document, bodyFont, homepage);
            // 诊断：打印国临码（有则填）
            document.add(new Paragraph(" ", bodyFont));
            document.add(new Paragraph("出院/入院诊断（国临码）", bodyFont));
            this.appendDiagnosisLines(document, bodyFont, homepage.getDiagnoses());
            // 手术：打印国临码（有则填）
            document.add(new Paragraph(" ", bodyFont));
            document.add(new Paragraph("手术/操作（国临码）", bodyFont));
            this.appendProcedureLines(document, bodyFont, homepage.getProcedures());
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
     * 追加中医信息段（国标码值转中文；西医关闭或五字段全空则不输出）。
     *
     * @param document 文档
     * @param bodyFont 正文字体
     * @param homepage 首页 DTO
     * @throws DocumentException 写入失败
     */
    private void appendTcmInfoSection(Document document, Font bodyFont, HomepageDto homepage)
            throws DocumentException {
        // Gate：西医院（或未判定为中医）不输出中医信息段
        if (!this.resolveTcmInfoEnabled(homepage)) {
            return;
        }
        String clinicalPath = displayClinicalPath(homepage.getClinicalPathCode());
        String tcmPrep = displayYesNo12(homepage.getTcmPrepCode());
        String tcmDevice = displayYesNo12(homepage.getTcmDeviceCode());
        String tcmTech = displayYesNo12(homepage.getTcmTechCode());
        String syndromeNurse = displayYesNo12(homepage.getSyndromeNurseCode());
        // 五字段皆空则不占打印行
        if (clinicalPath.isEmpty()
                && tcmPrep.isEmpty()
                && tcmDevice.isEmpty()
                && tcmTech.isEmpty()
                && syndromeNurse.isEmpty()) {
            return;
        }
        document.add(new Paragraph(" ", bodyFont));
        document.add(new Paragraph("中医信息", bodyFont));
        document.add(new Paragraph("实施临床路径：" + clinicalPath, bodyFont));
        document.add(new Paragraph("使用医疗机构中药制剂：" + tcmPrep, bodyFont));
        document.add(new Paragraph("使用中医诊疗设备：" + tcmDevice, bodyFont));
        document.add(new Paragraph("使用中医诊疗技术：" + tcmTech, bodyFont));
        document.add(new Paragraph("辨证施护：" + syndromeNurse, bodyFont));
    }

    /**
     * 解析是否启用中医信息段：DTO 已带开关则用之，否则按医院 medins_type 现算。
     *
     * @param homepage 首页 DTO；可空
     * @return true 表示中医院口径
     */
    private boolean resolveTcmInfoEnabled(HomepageDto homepage) {
        if (homepage != null && homepage.getTcmInfoEnabled() != null) {
            return Boolean.TRUE.equals(homepage.getTcmInfoEnabled());
        }
        return hospitalTcmInfoEnabledResolver.resolveTcmInfoEnabled();
    }

    /**
     * 实施临床路径码 → 展示文案（1中医 2西医 3否）。
     *
     * @param code 持久化码
     * @return 展示文案；未知码回显原码；空则空串
     */
    private String displayClinicalPath(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        if ("1".equals(code)) {
            return "中医";
        }
        if ("2".equals(code)) {
            return "西医";
        }
        if ("3".equals(code)) {
            return "否";
        }
        return code;
    }

    /**
     * 是否码 → 展示文案（1是 2否）。
     *
     * @param code 持久化码
     * @return 展示文案；未知码回显原码；空则空串
     */
    private String displayYesNo12(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        if ("1".equals(code)) {
            return "是";
        }
        if ("2".equals(code)) {
            return "否";
        }
        return code;
    }

    /**
     * 追加诊断行（国临码 + 名称）；无数据写占位句。
     *
     * @param document 文档
     * @param bodyFont 正文字体
     * @param diagnoses 诊断行
     * @throws DocumentException 写入失败
     */
    private void appendDiagnosisLines(Document document, Font bodyFont, List<DiagnosisLineDto> diagnoses)
            throws DocumentException {
        if (diagnoses == null || diagnoses.isEmpty()) {
            document.add(new Paragraph("（无）", bodyFont));
            return;
        }
        for (DiagnosisLineDto row : diagnoses) {
            String code = nullToEmpty(row.getDiagCode());
            String name = nullToEmpty(row.getDiagName());
            String type = nullToEmpty(row.getDiagTypeCode());
            document.add(new Paragraph(type + " " + code + " " + name, bodyFont));
        }
    }

    /**
     * 追加手术行（国临码 + 名称）；无数据写占位句。
     *
     * @param document 文档
     * @param bodyFont 正文字体
     * @param procedures 手术行
     * @throws DocumentException 写入失败
     */
    private void appendProcedureLines(Document document, Font bodyFont, List<ProcedureLineDto> procedures)
            throws DocumentException {
        if (procedures == null || procedures.isEmpty()) {
            document.add(new Paragraph("（无）", bodyFont));
            return;
        }
        for (ProcedureLineDto row : procedures) {
            String code = nullToEmpty(row.getOprnOprtCode());
            String name = nullToEmpty(row.getOprnOprtName());
            document.add(new Paragraph(code + " " + name, bodyFont));
        }
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
