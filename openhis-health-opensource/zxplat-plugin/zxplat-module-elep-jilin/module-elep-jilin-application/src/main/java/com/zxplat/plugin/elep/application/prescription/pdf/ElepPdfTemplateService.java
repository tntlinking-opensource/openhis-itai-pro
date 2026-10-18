/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.application.prescription.pdf;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Image;
import com.itextpdf.text.pdf.BarcodeQRCode;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.zxplat.plugin.elep.domain.prescription.result.ElepPdfInput;
import com.zxplat.framework.core.application.exception.BusinessException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.List;

/**
 * PDF 模板填充（自 ybapp {@code PDFTemplateFillerUtil} 迁移）。
 */
public final class ElepPdfTemplateService {

    public static final String DASH_LINE =
            "-------------------------------------------------------------------------------------------------------";

    private ElepPdfTemplateService() {
    }

    public static void fillPdfTemplate(
            String templatePath,
            String outputPath,
            String hospitalSealPath,
            ElepPdfInput dto) {
        try {
            ensureParentDir(outputPath);
            PdfReader reader = new PdfReader(templatePath);
            PdfStamper stamper = new PdfStamper(reader, new FileOutputStream(outputPath));

            BaseFont baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            setFieldFont(stamper, baseFont, "$HOS_NAME", 14f);
            setFieldFont(stamper, baseFont, "$QR_NUMBER", 10f);
            setFieldFont(stamper, baseFont, "$BUS_NO", 10f);
            setFieldFont(stamper, baseFont, "$P_NO", 10f);
            setFieldFont(stamper, baseFont, "$LOCATION_NAME", 10f);
            setFieldFont(stamper, baseFont, "$GENDER", 10f);
            setFieldFont(stamper, baseFont, "$AGE", 10f);
            setFieldFont(stamper, baseFont, "$CONTRACT", 8f);
            setFieldFont(stamper, baseFont, "$DATE", 10f);
            setFieldFont(stamper, baseFont, "$DAYS", 10f);
            setFieldFont(stamper, baseFont, "$DIAG_NAME", 10f);

            stamper.getAcroFields().setField("$QR_NUMBER", dto.getRxTraceCode());
            stamper.getAcroFields().setField("$HOS_NAME", dto.getOrgName());
            stamper.getAcroFields().setField("$BUS_NO", dto.getIptOtpNo());
            stamper.getAcroFields().setField("$P_NO", dto.getPrescriptionNo());
            stamper.getAcroFields().setField("$LOCATION_NAME", dto.getLocationName());
            stamper.getAcroFields().setField("$PATIENT_NAME", dto.getPatnName());
            stamper.getAcroFields().setField("$GENDER", dto.getGender());
            if (dto.getPatnAge() != null) {
                stamper.getAcroFields().setField("$AGE", dto.getPatnAge().toPlainString() + "岁");
            }
            stamper.getAcroFields().setField("$CONTRACT", dto.getHiFeesetlType());
            if (dto.getPrscTime() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日");
                stamper.getAcroFields().setField("$DATE", sdf.format(dto.getPrscTime()));
            }
            stamper.getAcroFields().setField("$DIAG_NAME", " " + nullToEmpty(dto.getDiagName()));
            if (dto.getValiDays() != null) {
                stamper.getAcroFields().setField("$DAYS", dto.getValiDays().toPlainString() + "天");
            }
            stamper.getAcroFields().setField("$REASON", dto.getReason());
            stamper.getAcroFields().setField("$P1_NAME", dto.getPrscDrName());
            stamper.getAcroFields().setField("$P2_NAME", dto.getPharName());
            stamper.getAcroFields().setField("$P3_NAME", dto.getDisRevPharName());
            stamper.getAcroFields().setField("$P4_NAME", dto.getCheckPharName());

            fillMedDetails(stamper, baseFont, dto.getMedDetailList());

            for (int i = 1; i < 6; i++) {
                String empty = stamper.getAcroFields().getField("$EMPTY_" + i);
                if (empty == null || empty.isEmpty()) {
                    stamper.getAcroFields().removeField("$LINE_" + i);
                    stamper.getAcroFields().removeField("$EMPTY_" + i);
                }
            }

            PdfContentByte cb = stamper.getOverContent(1);
            if (dto.getRxTraceCode() != null && !dto.getRxTraceCode().isBlank()) {
                BarcodeQRCode qrCode = new BarcodeQRCode(dto.getRxTraceCode(), 100, 100, null);
                Image qrImage = qrCode.getImage();
                qrImage.setAbsolutePosition(80, 680);
                cb.addImage(qrImage);
            }

            addHospitalSeal(cb, reader, hospitalSealPath);

            stamper.close();
            reader.close();
        } catch (IOException | DocumentException e) {
            throw new BusinessException("ELEP_PDF_FILL_FAILED", "填充 PDF 失败: " + e.getMessage());
        }
    }

    public static String readPdfBase64(String filePath) {
        File file = new File(filePath);
        if (!file.exists() || !file.isFile() || !file.getName().toLowerCase().endsWith(".pdf")) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] fileBytes = fis.readAllBytes();
            return Base64.getEncoder().encodeToString(fileBytes);
        } catch (IOException e) {
            return null;
        }
    }

    public static void writeSignedPdf(String rxFileBase64, String filePath) {
        if (rxFileBase64 == null || rxFileBase64.isBlank()) {
            throw new BusinessException("ELEP_PDF_EMPTY", "签名后 rxFile 为空");
        }
        try {
            byte[] pdfBytes = Base64.getDecoder().decode(rxFileBase64);
            ensureParentDir(filePath);
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                fos.write(pdfBytes);
            }
        } catch (Exception e) {
            throw new BusinessException("ELEP_PDF_WRITE_FAILED", "写入签名 PDF 失败: " + e.getMessage());
        }
    }

    private static void fillMedDetails(PdfStamper stamper, BaseFont baseFont, List<ElepPdfInput.MedDetail> medDetails)
            throws IOException, DocumentException {
        if (medDetails == null || medDetails.isEmpty()) {
            return;
        }
        for (int i = 0; i < medDetails.size(); i++) {
            ElepPdfInput.MedDetail medDetail = medDetails.get(i);
            int index = i + 1;
            String strName = nullToEmpty(medDetail.getMedName()) + "   " + nullToEmpty(medDetail.getDrugSpec());
            String strDose = "用法： ";
            if (medDetail.getSinDoscnt() != null && medDetail.getSinDosunt() != null) {
                strDose = strDose + medDetail.getSinDoscnt() + medDetail.getSinDosunt();
            }
            strDose = strDose + "   " + nullToEmpty(medDetail.getUsedFrquCodg())
                    + "   " + nullToEmpty(medDetail.getMedWay());

            setFieldFont(stamper, baseFont, "$NAME_" + index, 10f);
            setFieldFont(stamper, baseFont, "$DOSE_" + index, 10f);
            stamper.getAcroFields().setField("$NAME_" + index, strName);
            stamper.getAcroFields().setField("$DOSE_" + index, strDose);
            if (i == medDetails.size() - 1) {
                setFieldFont(stamper, baseFont, "$LINE_" + index, 13f);
                setFieldFont(stamper, baseFont, "$EMPTY_" + index, 7f);
                stamper.getAcroFields().setField("$LINE_" + index, DASH_LINE);
                stamper.getAcroFields().setField("$EMPTY_" + index, "(以下是空白)");
            }
        }
    }

    private static void addHospitalSeal(PdfContentByte cb, PdfReader reader, String hospitalSealPath)
            throws DocumentException {
        if (hospitalSealPath == null || hospitalSealPath.isBlank()) {
            return;
        }
        try {
            Image hospitalSeal = Image.getInstance(hospitalSealPath);
            hospitalSeal.scaleToFit(120, 100);
            float pageSizeWidth = reader.getPageSize(1).getWidth();
            float pageSizeHeight = reader.getPageSize(1).getHeight();
            int sealX = (int) (pageSizeWidth - hospitalSeal.getScaledWidth() - 80);
            int sealY = (int) (pageSizeHeight - hospitalSeal.getScaledHeight() - 550);
            hospitalSeal.setAbsolutePosition(sealX, sealY);
            cb.addImage(hospitalSeal);
        } catch (Exception ignored) {
            // 盖章失败不阻断主流程（对齐 ybapp）
        }
    }

    private static void setFieldFont(PdfStamper stamper, BaseFont baseFont, String field, float size)
            throws IOException, DocumentException {
        stamper.getAcroFields().setFieldProperty(field, "textfont", baseFont, null);
        stamper.getAcroFields().setFieldProperty(field, "textsize", size, null);
    }

    private static void ensureParentDir(String filePath) throws IOException {
        Path parent = Paths.get(filePath).getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
