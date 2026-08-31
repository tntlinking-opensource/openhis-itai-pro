/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.support;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 团检名册导入 xlsx 模板生成器。
 */
@Component
public class GroupRosterImportTemplateBuilder {

    /**
     * 生成指定人员类型的导入模板。
     *
     * @param memberKind 学生 / 企事业
     * @return xlsx 字节
     */
    public byte[] build(GroupRosterImportMemberKind memberKind) {
        if (memberKind == null) {
            throw new IllegalArgumentException("memberKind 不能为空");
        }
        String[] headers = resolveHeaders(memberKind);
        String[] example = resolveExample(memberKind);
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet guideSheet = workbook.createSheet(GroupRosterImportConstants.SHEET_GUIDE);
            Sheet dataSheet = workbook.createSheet(GroupRosterImportConstants.SHEET_DATA);
            Sheet sysSheet = workbook.createSheet(GroupRosterImportConstants.SHEET_SYS);
            fillGuideSheet(guideSheet, memberKind);
            fillDataSheet(dataSheet, workbook, headers, example);
            fillSysSheet(sysSheet, memberKind);
            workbook.setSheetHidden(workbook.getSheetIndex(sysSheet), true);
            workbook.setActiveSheet(workbook.getSheetIndex(dataSheet));
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("生成团检名册导入模板失败", ex);
        }
    }

    private void fillGuideSheet(Sheet sheet, GroupRosterImportMemberKind memberKind) {
        String kindLabel = resolveKindLabel(memberKind);
        String noLabel = memberKind == GroupRosterImportMemberKind.STUDENT ? "学号" : "工号";
        String campusNote = memberKind == GroupRosterImportMemberKind.STAFF
            ? "教职工模板导入时将同步写入 Campus 教职工档案，部门须与 Campus 部门名称一致。"
            : "";
        String[][] lines = {
            {"团检名册导入模板 " + GroupRosterImportConstants.TEMPLATE_VERSION},
            {"人员类型：" + kindLabel},
            {"填写说明："},
            {"1. 请在「名册」工作表填写数据，带 * 的列为必填。"},
            {"2. 性别可填：男、女、未知（或 MALE/FEMALE/UNKNOWN）。"},
            {"3. 身份证、手机号、" + noLabel + " 至少填一项，用于匹配或创建患者档案。"},
            {"4. 第 2 行为示例行，导入前请删除或覆盖；含「示例」字样的行会自动跳过。"},
            {"5. 上传前请确认已在系统中选好对应批次，并绑定套餐（若需导入后自动建单）。"},
        };
        int rowIndex = 0;
        for (String[] line : lines) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(line[0]);
        }
        if (!campusNote.isEmpty()) {
            Row row = sheet.createRow(rowIndex);
            row.createCell(0).setCellValue("6. " + campusNote);
        }
        sheet.setColumnWidth(0, 12000);
    }

    private static String resolveKindLabel(GroupRosterImportMemberKind memberKind) {
        if (memberKind == GroupRosterImportMemberKind.STUDENT) {
            return "学生";
        }
        if (memberKind == GroupRosterImportMemberKind.STAFF) {
            return "教职工";
        }
        return "企事业";
    }

    private static String[] resolveHeaders(GroupRosterImportMemberKind memberKind) {
        if (memberKind == GroupRosterImportMemberKind.STUDENT) {
            return GroupRosterImportConstants.STUDENT_HEADERS;
        }
        if (memberKind == GroupRosterImportMemberKind.ENTERPRISE) {
            return GroupRosterImportConstants.ENTERPRISE_HEADERS;
        }
        return GroupRosterImportConstants.STAFF_HEADERS;
    }

    private static String[] resolveExample(GroupRosterImportMemberKind memberKind) {
        if (memberKind == GroupRosterImportMemberKind.STUDENT) {
            return GroupRosterImportConstants.STUDENT_EXAMPLE;
        }
        if (memberKind == GroupRosterImportMemberKind.ENTERPRISE) {
            return GroupRosterImportConstants.ENTERPRISE_EXAMPLE;
        }
        return GroupRosterImportConstants.STAFF_EXAMPLE;
    }

    private void fillDataSheet(
            Sheet sheet, XSSFWorkbook workbook, String[] headers, String[] example) {
        CellStyle headerStyle = createHeaderStyle(workbook);
        Row headerRow = sheet.createRow(0);
        for (int col = 0; col < headers.length; col++) {
            Cell cell = headerRow.createCell(col);
            cell.setCellValue(headers[col]);
            cell.setCellStyle(headerStyle);
        }
        Row exampleRow = sheet.createRow(1);
        for (int col = 0; col < example.length; col++) {
            exampleRow.createCell(col).setCellValue(example[col]);
        }
        for (int col = 0; col < headers.length; col++) {
            sheet.setColumnWidth(col, 4200);
        }
        sheet.setColumnWidth(2, 5600);
        sheet.createFreezePane(0, 1);
    }

    private void fillSysSheet(Sheet sheet, GroupRosterImportMemberKind memberKind) {
        Row kindRow = sheet.createRow(0);
        kindRow.createCell(0).setCellValue(GroupRosterImportConstants.SYS_KIND);
        kindRow.createCell(1).setCellValue(memberKind.name());
        Row versionRow = sheet.createRow(1);
        versionRow.createCell(0).setCellValue(GroupRosterImportConstants.SYS_VERSION);
        versionRow.createCell(1).setCellValue(GroupRosterImportConstants.TEMPLATE_VERSION);
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }
}
