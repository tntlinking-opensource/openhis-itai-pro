/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.devicecatalog.support;

import com.openhis.health.domain.share.constants.DeviceCatalogImportConstants;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 耗材目录导入 xlsx 模板生成器（模板 v1.0：全字段对齐新增耗材表单，33 列）。
 */
@Service
@RequiredArgsConstructor
public class DeviceCatalogImportTemplateBuilder {

    private static final int OPTION_DATA_START_ROW = 1;

    private final DeviceCatalogImportTerminologyIndex terminologyIndex;

    /**
     * 生成导入模板字节（含指导页、双行表头、示例、下拉、指纹与隐藏 _sys）。
     *
     * @return xlsx 字节
     */
    public byte[] build() {
        terminologyIndex.load();
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet guideSheet = workbook.createSheet(DeviceCatalogImportConstants.SHEET_GUIDE);
            Sheet dataSheet = workbook.createSheet(DeviceCatalogImportConstants.SHEET_DATA);
            Sheet optionsSheet = workbook.createSheet(DeviceCatalogImportConstants.SHEET_OPTIONS);
            Sheet sysSheet = workbook.createSheet(DeviceCatalogImportConstants.SHEET_SYS);
            this.fillGuideSheet(guideSheet);
            this.fillOptionsSheet(optionsSheet, workbook);
            this.fillDataSheet((XSSFSheet) dataSheet, workbook);
            this.fillSysSheet(sysSheet);
            workbook.setSheetHidden(workbook.getSheetIndex(sysSheet), true);
            guideSheet.protectSheet("");
            optionsSheet.protectSheet("");
            sysSheet.protectSheet("");
            workbook.setActiveSheet(workbook.getSheetIndex(guideSheet));
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("生成耗材目录导入模板失败", ex);
        }
    }

    /**
     * 生成导出 xlsx：单 Sheet；首列编号 + 导入字段序；表头下一行起数据。
     *
     * @param dataRows 导出行；null 视为空
     * @return xlsx 字节
     */
    public byte[] buildExport(List<String[]> dataRows) {
        List<String[]> rows = dataRows;
        if (rows == null) {
            rows = List.of();
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(DeviceCatalogImportConstants.EXPORT_SHEET_NAME);
            CellStyle headerStyle = this.createHeaderStyle(workbook, IndexedColors.GREY_25_PERCENT, false);
            String[] exportHeaders = this.buildExportHeaders();
            Row headerRow = sheet.createRow(0);
            for (int col = 0; col < exportHeaders.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(exportHeaders[col]);
                cell.setCellStyle(headerStyle);
            }
            int excelRow = 1;
            for (String[] values : rows) {
                this.fillExportDataRow(sheet, excelRow, values, exportHeaders.length);
                excelRow++;
            }
            this.applyColumnWidths(sheet);
            sheet.createFreezePane(1, 1);
            workbook.setActiveSheet(0);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("生成耗材目录导出文件失败", ex);
        }
    }

    /**
     * 导出表头：编号 + 导入 COLUMN_HEADERS。
     *
     * @return 表头
     */
    private String[] buildExportHeaders() {
        String[] importHeaders = DeviceCatalogImportConstants.COLUMN_HEADERS;
        String[] exportHeaders = new String[importHeaders.length + 1];
        exportHeaders[0] = DeviceCatalogImportConstants.EXPORT_BUS_NO_HEADER;
        System.arraycopy(importHeaders, 0, exportHeaders, 1, importHeaders.length);
        return exportHeaders;
    }

    /**
     * 写入导出行。
     *
     * @param sheet    Sheet
     * @param rowIndex 行号
     * @param values   值
     * @param colCount 列数
     */
    private void fillExportDataRow(Sheet sheet, int rowIndex, String[] values, int colCount) {
        Row row = sheet.createRow(rowIndex);
        if (values == null) {
            return;
        }
        for (int col = 0; col < colCount; col++) {
            Cell cell = row.createCell(col);
            if (col < values.length && values[col] != null) {
                cell.setCellValue(values[col]);
            }
        }
    }

    /**
     * 填写指导页。
     */
    private void fillGuideSheet(Sheet sheet) {
        sheet.createRow(0).createCell(0).setCellValue("耗材目录批量导入 — 填写说明");
        Row versionRow = sheet.createRow(1);
        versionRow.createCell(1).setCellValue("模板版本 " + DeviceCatalogImportConstants.TEMPLATE_VERSION);
        sheet.createRow(2).createCell(0).setCellValue("第一步：阅读本页说明，了解拆零与价格填写规则");
        sheet.createRow(3).createCell(0).setCellValue("第二步：切换到「②耗材目录」，从第 5 行开始填写（使用下拉选中文）");
        sheet.createRow(4).createCell(0).setCellValue("第三步：保存后回到系统上传本文件完成导入");
        sheet.createRow(6).createCell(0).setCellValue(
            "拆零对照示例：1 盒 = 100 支 → 采购单位=盒，拆零单位=支，采购/拆零倍数=100；包装单位通常=采购单位");
        Row demoHeader = sheet.createRow(7);
        demoHeader.createCell(0).setCellValue("采购单位");
        demoHeader.createCell(1).setCellValue("拆零单位");
        demoHeader.createCell(2).setCellValue("采购/拆零倍数");
        demoHeader.createCell(3).setCellValue("包装单位");
        Row demoRow = sheet.createRow(8);
        demoRow.createCell(0).setCellValue("盒");
        demoRow.createCell(1).setCellValue("支");
        demoRow.createCell(2).setCellValue("100");
        demoRow.createCell(3).setCellValue("盒");
        sheet.createRow(10).createCell(0).setCellValue("FAQ：下拉列只选中文名称，不要手填系统编码");
        sheet.createRow(11).createCell(0).setCellValue("FAQ：购入价/零售价单位是「元/一个包装单位」（不是拆零单价）");
        sheet.createRow(12).createCell(0).setCellValue("FAQ：耗材名称以 [示例] 开头的行导入时会自动跳过");
        sheet.createRow(13).createCell(0).setCellValue("FAQ：导入失败可下载「错误标注表」对照修改后整文件重传");
        sheet.createRow(14).createCell(0).setCellValue("FAQ：勿改第 1～2 行表头（分组与字段名），否则需重新下载模板");
        sheet.createRow(15).createCell(0).setCellValue("FAQ：耗材编号、拼音/五笔码由系统生成；导入成功后状态为已启用");
        sheet.setColumnWidth(0, 16000);
    }

    /**
     * 填写可选值一览并创建命名区域。
     */
    private void fillOptionsSheet(Sheet sheet, XSSFWorkbook workbook) {
        this.writeOptionColumn(sheet, 0, "耗材分类",
            terminologyIndex.labelsFor(DeviceCatalogImportTerminologyIndex.DICT_CATEGORY));
        this.writeOptionColumn(sheet, 1, "耗材种类",
            terminologyIndex.labelsFor(DeviceCatalogImportTerminologyIndex.DICT_DEVICE_TYPE));
        // 采购/拆零/包装/库存共用 unit_code 一列 labels
        this.writeOptionColumn(sheet, 2, "计量单位",
            terminologyIndex.labelsFor(DeviceCatalogImportTerminologyIndex.DICT_UNIT));
        this.writeOptionColumn(sheet, 3, "财务类型",
            terminologyIndex.labelsFor(DeviceCatalogImportTerminologyIndex.DICT_FIN_TYPE));
        this.writeOptionColumn(sheet, 4, "是/否",
            terminologyIndex.labelsFor(DeviceCatalogImportTerminologyIndex.DICT_YES_NO));
        this.writeOptionColumn(sheet, 5, "医保等级",
            terminologyIndex.labelsFor(DeviceCatalogImportTerminologyIndex.DICT_CHRGITM_LV));
        this.writeOptionColumn(sheet, 6, "病案结算类型",
            terminologyIndex.labelsFor(DeviceCatalogImportTerminologyIndex.DICT_MR_SETTLEMENT));
        this.writeOptionColumn(sheet, 7, "医保类别",
            terminologyIndex.labelsFor(DeviceCatalogImportTerminologyIndex.DICT_YB_TYPE));
        this.createNamedRanges(workbook);
    }

    /**
     * 填写数据录入 Sheet：分组表头、字段表头、示例、下拉与批注。
     */
    private void fillDataSheet(XSSFSheet dataSheet, XSSFWorkbook workbook) {
        CellStyle requiredHeaderStyle = this.createHeaderStyle(workbook, IndexedColors.CORAL, true);
        CellStyle optionalHeaderStyle = this.createHeaderStyle(workbook, IndexedColors.GREY_25_PERCENT, false);
        CellStyle exampleStyle = this.createExampleStyle(workbook);
        Set<String> requiredSet = new HashSet<>(Arrays.asList(DeviceCatalogImportConstants.REQUIRED_HEADERS));
        Row groupRow = dataSheet.createRow(0);
        this.mergeAndLabel(dataSheet, groupRow, 0, 13, "基础信息");
        this.mergeAndLabel(dataSheet, groupRow, 14, 25, "库存与单位（价格：元/包装单位）");
        this.mergeAndLabel(dataSheet, groupRow, 26, 32, "业务信息");
        Row headerRow = dataSheet.createRow(DeviceCatalogImportConstants.HEADER_ROW_INDEX);
        for (int col = 0; col < DeviceCatalogImportConstants.COLUMN_HEADERS.length; col++) {
            String header = DeviceCatalogImportConstants.COLUMN_HEADERS[col];
            Cell cell = headerRow.createCell(col);
            if (requiredSet.contains(header)) {
                cell.setCellValue("*" + header);
                cell.setCellStyle(requiredHeaderStyle);
            } else {
                cell.setCellValue(header);
                cell.setCellStyle(optionalHeaderStyle);
            }
        }
        this.fillExampleRow(dataSheet, 2, exampleStyle, this.exampleSyringeValues());
        this.fillExampleRow(dataSheet, 3, exampleStyle, this.exampleInfusionValues());
        this.addHeaderComments(dataSheet, workbook);
        this.applyColumnWidths(dataSheet);
        dataSheet.createFreezePane(1, 2);
        this.applyDropdownValidations(dataSheet);
    }

    /**
     * 示例行：一次性注射器。
     */
    private String[] exampleSyringeValues() {
        String[] values = new String[DeviceCatalogImportConstants.COLUMN_HEADERS.length];
        values[0] = "[示例]一次性注射器";
        values[1] = "某某注射器";
        values[2] = "注射穿刺器械";
        values[3] = "普通耗材";
        values[4] = "某某医疗器械有限公司";
        values[5] = "材料费";
        values[6] = "5ml*100支/盒";
        values[13] = "盒";
        values[14] = "支";
        values[15] = "100";
        values[16] = "盒";
        values[17] = "100";
        values[18] = "支";
        values[19] = "1";
        values[20] = "35";
        values[21] = "48";
        return values;
    }

    /**
     * 示例行：输液器。
     */
    private String[] exampleInfusionValues() {
        String[] values = new String[DeviceCatalogImportConstants.COLUMN_HEADERS.length];
        values[0] = "[示例]输液器";
        values[1] = "某某输液器";
        values[2] = "注射穿刺器械";
        values[3] = "普通耗材";
        values[4] = "某某医疗器械股份有限公司";
        values[5] = "材料费";
        values[6] = "普通型*50套/箱";
        values[13] = "箱";
        values[14] = "套";
        values[15] = "50";
        values[16] = "箱";
        values[17] = "50";
        values[18] = "套";
        values[19] = "1";
        values[20] = "80";
        values[21] = "110";
        return values;
    }

    /**
     * 填写隐藏 _sys 元数据。
     */
    private void fillSysSheet(Sheet sysSheet) {
        Row row1 = sysSheet.createRow(0);
        row1.createCell(0).setCellValue("TEMPLATE_CODE");
        row1.createCell(1).setCellValue(DeviceCatalogImportConstants.TEMPLATE_CODE);
        Row row2 = sysSheet.createRow(1);
        row2.createCell(0).setCellValue("TEMPLATE_VERSION");
        row2.createCell(1).setCellValue(DeviceCatalogImportConstants.TEMPLATE_VERSION);
        Row row3 = sysSheet.createRow(2);
        row3.createCell(0).setCellValue("COLUMN_FINGERPRINT");
        row3.createCell(1).setCellValue(DeviceCatalogImportWorkbookParser.computeFingerprint());
    }

    /**
     * 写入一列可选值，返回下一空行行号。
     */
    private int writeOptionColumn(Sheet sheet, int colIndex, String title, List<String> labels) {
        Row titleRow = sheet.getRow(0);
        if (titleRow == null) {
            titleRow = sheet.createRow(0);
        }
        titleRow.createCell(colIndex).setCellValue(title);
        int rowIndex = OPTION_DATA_START_ROW;
        if (labels != null) {
            for (String label : labels) {
                if (StringUtils.isEmpty(label)) {
                    continue;
                }
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    row = sheet.createRow(rowIndex);
                }
                row.createCell(colIndex).setCellValue(label);
                rowIndex++;
            }
        }
        if (rowIndex == OPTION_DATA_START_ROW) {
            return OPTION_DATA_START_ROW + 1;
        }
        return rowIndex;
    }

    /**
     * 创建下拉命名区域。
     */
    private void createNamedRanges(XSSFWorkbook workbook) {
        String sheetName = DeviceCatalogImportConstants.SHEET_OPTIONS;
        this.createNamedRange(workbook, "OPT_CATEGORY", sheetName, 0,
            this.countLabels(DeviceCatalogImportTerminologyIndex.DICT_CATEGORY));
        this.createNamedRange(workbook, "OPT_DEVICE_TYPE", sheetName, 1,
            this.countLabels(DeviceCatalogImportTerminologyIndex.DICT_DEVICE_TYPE));
        // 四类单位列共用同一 OPT_UNIT 命名区域
        this.createNamedRange(workbook, "OPT_UNIT", sheetName, 2,
            this.countLabels(DeviceCatalogImportTerminologyIndex.DICT_UNIT));
        this.createNamedRange(workbook, "OPT_FIN_TYPE", sheetName, 3,
            this.countLabels(DeviceCatalogImportTerminologyIndex.DICT_FIN_TYPE));
        this.createNamedRange(workbook, "OPT_YES_NO", sheetName, 4,
            this.countLabels(DeviceCatalogImportTerminologyIndex.DICT_YES_NO));
        this.createNamedRange(workbook, "OPT_CHRGITM_LV", sheetName, 5,
            this.countLabels(DeviceCatalogImportTerminologyIndex.DICT_CHRGITM_LV));
        this.createNamedRange(workbook, "OPT_MR_SETTLE", sheetName, 6,
            this.countLabels(DeviceCatalogImportTerminologyIndex.DICT_MR_SETTLEMENT));
        this.createNamedRange(workbook, "OPT_YB_TYPE", sheetName, 7,
            this.countLabels(DeviceCatalogImportTerminologyIndex.DICT_YB_TYPE));
    }

    /**
     * 统计字典 label 数量对应的 Excel 结束行（1-based）。
     */
    private int countLabels(String dictKey) {
        List<String> labels = terminologyIndex.labelsFor(dictKey);
        if (labels == null || labels.isEmpty()) {
            return OPTION_DATA_START_ROW + 1;
        }
        return OPTION_DATA_START_ROW + labels.size();
    }

    /**
     * 创建命名区域公式。
     */
    private void createNamedRange(XSSFWorkbook workbook, String name, String sheetName, int colIndex, int endRowExcel) {
        Name namedRange = workbook.createName();
        namedRange.setNameName(name);
        String colLetter = this.columnLetter(colIndex);
        String refersTo = "'" + sheetName + "'!$" + colLetter + "$2:$" + colLetter + "$" + endRowExcel;
        namedRange.setRefersToFormula(refersTo);
    }

    /**
     * 为数据列挂载下拉验证。
     */
    private void applyDropdownValidations(XSSFSheet dataSheet) {
        int firstRow = DeviceCatalogImportConstants.DATA_START_ROW_INDEX;
        int lastRow = DeviceCatalogImportConstants.VALIDATION_END_ROW - 1;
        DataValidationHelper helper = dataSheet.getDataValidationHelper();
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 2, "OPT_CATEGORY", "请选择耗材分类");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 3, "OPT_DEVICE_TYPE", "请选择耗材种类");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 5, "OPT_FIN_TYPE", "请选择财务类型");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 9, "OPT_CHRGITM_LV", "请选择医保等级");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 10, "OPT_MR_SETTLE", "请选择病案结算类型");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 11, "OPT_YES_NO", "请选择是否医保对码");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 12, "OPT_YB_TYPE", "请选择医保类别");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 13, "OPT_UNIT", "请选择采购单位");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 14, "OPT_UNIT", "请选择拆零单位");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 16, "OPT_UNIT", "请选择包装单位");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 18, "OPT_UNIT", "请选择库存单位");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 27, "OPT_YES_NO", "请选择过敏标记");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 28, "OPT_YES_NO", "请选择医保标记");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 29, "OPT_YES_NO", "请选择高值耗材标志");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 30, "OPT_YES_NO", "请选择不收费耗材");
    }

    /**
     * 添加列表下拉验证。
     */
    private void addListValidation(
        XSSFSheet sheet,
        DataValidationHelper helper,
        int firstRow,
        int lastRow,
        int colIndex,
        String namedRange,
        String prompt
    ) {
        DataValidationConstraint constraint = helper.createFormulaListConstraint(namedRange);
        CellRangeAddressList addressList = new CellRangeAddressList(firstRow, lastRow, colIndex, colIndex);
        DataValidation validation = helper.createValidation(constraint, addressList);
        validation.setSuppressDropDownArrow(true);
        validation.setShowErrorBox(true);
        validation.createPromptBox("填写提示", prompt);
        validation.setShowPromptBox(true);
        sheet.addValidationData(validation);
    }

    /**
     * 创建表头样式。
     */
    private CellStyle createHeaderStyle(XSSFWorkbook workbook, IndexedColors color, boolean boldWhite) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setBold(true);
        if (boldWhite) {
            font.setColor(IndexedColors.WHITE.getIndex());
        }
        style.setFont(font);
        return style;
    }

    /**
     * 创建示例行样式。
     */
    private CellStyle createExampleStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    /**
     * 合并分组单元格并写标签。
     */
    private void mergeAndLabel(Sheet sheet, Row row, int fromCol, int toCol, String label) {
        if (fromCol < toCol) {
            sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), fromCol, toCol));
        }
        row.createCell(fromCol).setCellValue(label);
    }

    /**
     * 填写示例行。
     */
    private void fillExampleRow(Sheet sheet, int rowIndex, CellStyle style, String[] values) {
        Row row = sheet.createRow(rowIndex);
        for (int col = 0; col < values.length; col++) {
            Cell cell = row.createCell(col);
            if (values[col] != null) {
                cell.setCellValue(values[col]);
            }
            cell.setCellStyle(style);
        }
    }

    /**
     * 为关键表头添加批注。
     */
    private void addHeaderComments(Sheet dataSheet, XSSFWorkbook workbook) {
        CreationHelper factory = workbook.getCreationHelper();
        Drawing<?> drawing = dataSheet.createDrawingPatriarch();
        this.putComment(dataSheet, drawing, factory, 6, "包装规格示例：5ml*100支/盒");
        this.putComment(dataSheet, drawing, factory, 16, "1个采购单位=多少个拆零单位，例如 1盒=100支 则填 100");
        this.putComment(dataSheet, drawing, factory, 18, "1个包装单位=多少个拆零单位；常与采购/拆零倍数相同");
        this.putComment(dataSheet, drawing, factory, 20, "1个库存单位=多少个拆零单位；库存=拆零时填 1");
        this.putComment(dataSheet, drawing, factory, 21, "购入价：元/一个包装单位（不是拆零单价）");
        this.putComment(dataSheet, drawing, factory, 22, "零售价：元/一个包装单位（不是拆零单价）");
        this.putComment(dataSheet, drawing, factory, 24, "警戒线最低值，单位为库存单位");
        this.putComment(dataSheet, drawing, factory, 25, "警戒线最高值，单位为库存单位");
        this.putComment(dataSheet, drawing, factory, 26, "院内旧编码仅作备注，不作为系统编码；可与说明同时填写");
    }

    /**
     * 在表头单元格写入批注。
     */
    private void putComment(
        Sheet sheet,
        Drawing<?> drawing,
        CreationHelper factory,
        int colIndex,
        String text
    ) {
        Row headerRow = sheet.getRow(DeviceCatalogImportConstants.HEADER_ROW_INDEX);
        if (headerRow == null) {
            return;
        }
        Cell cell = headerRow.getCell(colIndex);
        if (cell == null) {
            return;
        }
        ClientAnchor anchor = factory.createClientAnchor();
        anchor.setCol1(colIndex);
        anchor.setRow1(DeviceCatalogImportConstants.HEADER_ROW_INDEX);
        Comment comment = drawing.createCellComment(anchor);
        comment.setString(factory.createRichTextString(text));
        cell.setCellComment(comment);
    }

    /**
     * 设置常用列宽。
     */
    private void applyColumnWidths(Sheet dataSheet) {
        dataSheet.setColumnWidth(0, 6000);
        dataSheet.setColumnWidth(1, 4500);
        dataSheet.setColumnWidth(4, 7000);
        dataSheet.setColumnWidth(6, 5500);
        for (int col = 28; col <= 31; col++) {
            dataSheet.setColumnWidth(col, 3200);
        }
    }

    /**
     * 列索引转 Excel 列字母。
     */
    private String columnLetter(int colIndex) {
        int temp = colIndex;
        StringBuilder builder = new StringBuilder();
        while (temp >= 0) {
            builder.insert(0, (char) ('A' + (temp % 26)));
            temp = temp / 26 - 1;
        }
        return builder.toString();
    }
}
