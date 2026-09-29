/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.medicationcatalog.support;

import com.openhis.health.application.shared.catalog.CatalogExportNumericCellWriter;
import com.openhis.health.domain.share.constants.MedicationCatalogImportConstants;
import com.openhis.whale.core.application.dto.ExcelExportColumnDto;
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
 * 药品目录导入 xlsx 模板生成器；兼导出单 Sheet（{@link #buildExport}）。
 *
 * <p>计量单位统一 {@code unit_code}；剂量 {@code dose_unit}、DDD {@code ddd_unit} 分码表；DDD 值为自由文字。</p>
 */
@Service
@RequiredArgsConstructor
public class MedicationCatalogImportTemplateBuilder {

    /**
     * 导出 NUMERIC 列下标：由 {@link MedicationCatalogImportConstants#EXPORT_NUMERIC_HEADER_NAMES} 解析，
     * 禁止魔法数字下标；编号列不在集合内。
     */
    private static final Set<Integer> EXPORT_NUMERIC_COLUMNS =
        CatalogExportNumericCellWriter.resolveExportNumericColumnIndexes(
            MedicationCatalogImportConstants.COLUMN_HEADERS,
            MedicationCatalogImportConstants.EXPORT_NUMERIC_HEADER_NAMES);

    private final MedicationCatalogImportTerminologyIndex terminologyIndex;

    /**
     * 生成导入模板字节（含指导页、双行表头、示例、下拉、指纹与隐藏 _sys）。
     *
     * @return xlsx 字节
     */
    public byte[] build() {
        terminologyIndex.load();
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet guideSheet = workbook.createSheet(MedicationCatalogImportConstants.SHEET_GUIDE);
            Sheet dataSheet = workbook.createSheet(MedicationCatalogImportConstants.SHEET_DATA);
            Sheet optionsSheet = workbook.createSheet(MedicationCatalogImportConstants.SHEET_OPTIONS);
            Sheet sysSheet = workbook.createSheet(MedicationCatalogImportConstants.SHEET_SYS);
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
            throw new IllegalStateException("生成药品目录导入模板失败", ex);
        }
    }

    /**
     * 生成导出 xlsx：仅单 Sheet「药品目录」；第 1 行字段表头，第 2 行起数据，无示例/空行/下拉/指导页。
     *
     * <p>首列为「编号」（busNo，导入模板没有）；其后列名与导入 {@link MedicationCatalogImportConstants#COLUMN_HEADERS} 一致。
     * 本文件<strong>不是</strong>导入模板，不能直接当 IMP-02 上传。</p>
     *
     * @param dataRows 每行单元格文案；null 视为空列表
     * @return xlsx 字节
     */
    public byte[] buildExport(List<String[]> dataRows) {
        return this.buildExport(dataRows, null);
    }

    /**
     * 生成导出 xlsx；可按前端列清单裁剪列（空则全量；NUMERIC 下标随投影重映射）。
     *
     * @param dataRows       每行单元格文案；null 视为空列表
     * @param exportColumns  前端自选列；null/空则全部列
     * @return xlsx 字节
     */
    public byte[] buildExport(List<String[]> dataRows, List<ExcelExportColumnDto> exportColumns) {
        List<String[]> rows = dataRows;
        if (rows == null) {
            rows = List.of();
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(MedicationCatalogImportConstants.EXPORT_SHEET_NAME);
            CellStyle headerStyle = this.createHeaderStyle(workbook, IndexedColors.GREY_25_PERCENT, false);
            String[] fullHeaders = this.buildExportHeaders();
            List<Integer> selectedIndexes =
                CatalogExportNumericCellWriter.resolveSelectedExportColumnIndexes(fullHeaders, exportColumns);
            String[] exportHeaders =
                CatalogExportNumericCellWriter.projectExportHeaders(fullHeaders, selectedIndexes, exportColumns);
            Set<Integer> numericColumns =
                CatalogExportNumericCellWriter.remapNumericColumnIndexes(EXPORT_NUMERIC_COLUMNS, selectedIndexes);
            // 第 1 行：编号 + 导入同序字段表头（或前端裁剪后列）
            Row headerRow = sheet.createRow(0);
            for (int col = 0; col < exportHeaders.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(exportHeaders[col]);
                cell.setCellStyle(headerStyle);
            }
            // 第 2 行起紧挨写入数据，不留空行、不写示例
            int excelRow = 1;
            for (String[] values : rows) {
                String[] projected =
                    CatalogExportNumericCellWriter.projectExportRowValues(values, selectedIndexes);
                this.fillDataRow(sheet, excelRow, projected, exportHeaders.length, numericColumns);
                excelRow++;
            }
            this.applyColumnWidths(sheet);
            sheet.createFreezePane(1, 1);
            workbook.setActiveSheet(0);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("生成药品目录导出文件失败", ex);
        }
    }

    /**
     * 组装导出表头：首列编号 + 导入模板字段名序列。
     *
     * @return 导出表头数组
     */
    private String[] buildExportHeaders() {
        String[] importHeaders = MedicationCatalogImportConstants.COLUMN_HEADERS;
        String[] exportHeaders = new String[importHeaders.length + 1];
        exportHeaders[0] = MedicationCatalogImportConstants.EXPORT_BUS_NO_HEADER;
        System.arraycopy(importHeaders, 0, exportHeaders, 1, importHeaders.length);
        return exportHeaders;
    }

    /**
     * 填写导入模板说明页。
     *
     * @param sheet 指导 Sheet
     */
    private void fillGuideSheet(Sheet sheet) {
        sheet.createRow(0).createCell(0).setCellValue("药品目录批量导入 — 填写说明");
        Row versionRow = sheet.createRow(1);
        versionRow.createCell(1).setCellValue("模板版本 " + MedicationCatalogImportConstants.TEMPLATE_VERSION);
        sheet.createRow(2).createCell(0).setCellValue("第一步：阅读本页说明，了解拆零与价格填写规则");
        sheet.createRow(3).createCell(0).setCellValue("第二步：切换到「②药品目录」，从第 5 行开始填写（使用下拉选中文）");
        sheet.createRow(4).createCell(0).setCellValue("第三步：保存后回到系统上传本文件完成导入");
        sheet.createRow(6).createCell(0).setCellValue(
            "拆零对照示例：1 盒 = 24 片 → 采购单位=盒，拆零单位=片，采购/拆零倍数=24；包装单位通常=采购单位");
        Row demoHeader = sheet.createRow(7);
        demoHeader.createCell(0).setCellValue("采购单位");
        demoHeader.createCell(1).setCellValue("拆零单位");
        demoHeader.createCell(2).setCellValue("采购/拆零倍数");
        demoHeader.createCell(3).setCellValue("包装单位");
        Row demoRow = sheet.createRow(8);
        demoRow.createCell(0).setCellValue("盒");
        demoRow.createCell(1).setCellValue("片");
        demoRow.createCell(2).setCellValue("24");
        demoRow.createCell(3).setCellValue("盒");
        sheet.createRow(10).createCell(0).setCellValue("FAQ：下拉列优先选中文；单位/用法若码表没有，导入时会按填写值自动补齐字典");
        sheet.createRow(11).createCell(0).setCellValue("FAQ：购入价/零售价单位是「元/一个包装单位」（不是拆零单价）");
        sheet.createRow(12).createCell(0).setCellValue("FAQ：药品名称以 [示例] 开头的行导入时会自动跳过");
        sheet.createRow(13).createCell(0).setCellValue("FAQ：导入失败可下载「错误标注表」对照修改后整文件重传");
        sheet.createRow(14).createCell(0).setCellValue("FAQ：勿改第 1～2 行表头（分组与字段名），否则需重新下载模板");
        sheet.createRow(15).createCell(0).setCellValue("FAQ：药品编号、拼音/五笔码由系统生成；导入成功后状态为已启用");
        sheet.createRow(16).createCell(0).setCellValue("FAQ：DDD值为自由文字（与维护页一致），勿改成码表编码；DDD单位仍用下拉");
        sheet.createRow(17).createCell(0).setCellValue(
            "FAQ：输液标记填是/否；旧模板「注射药品」列同义兼容");
        sheet.createRow(18).createCell(0).setCellValue(
            "FAQ：追溯码单位/倍数必填；旧模板无此两列时导入按包装单位同阶回落");
        sheet.setColumnWidth(0, 16000);
    }

    private void fillOptionsSheet(Sheet sheet, XSSFWorkbook workbook) {
        this.writeOptionColumn(sheet, 0, "药品分类",
            terminologyIndex.labelsFor(MedicationCatalogImportTerminologyIndex.DICT_CATEGORY));
        this.writeOptionColumn(sheet, 1, "剂量单位",
            terminologyIndex.labelsFor(MedicationCatalogImportTerminologyIndex.DICT_DOSE_UNIT));
        // 采购/拆零/包装/库存四列下拉同源：统一计量单位 unit_code
        List<String> unitLabels =
            terminologyIndex.labelsFor(MedicationCatalogImportTerminologyIndex.DICT_UNIT);
        this.writeOptionColumn(sheet, 2, "采购单位", unitLabels);
        this.writeOptionColumn(sheet, 3, "拆零单位", unitLabels);
        this.writeOptionColumn(sheet, 4, "包装单位", unitLabels);
        this.writeOptionColumn(sheet, 5, "库存单位", unitLabels);
        this.writeOptionColumn(sheet, 6, "追溯码单位", unitLabels);
        this.writeOptionColumn(sheet, 7, "剂型",
            terminologyIndex.labelsFor(MedicationCatalogImportTerminologyIndex.DICT_DOSE_FORM));
        this.writeOptionColumn(sheet, 8, "默认用法",
            terminologyIndex.labelsFor(MedicationCatalogImportTerminologyIndex.DICT_METHOD));
        this.writeOptionColumn(sheet, 9, "默认频次",
            terminologyIndex.labelsFor(MedicationCatalogImportTerminologyIndex.DICT_RATE));
        this.writeOptionColumn(sheet, 10, "财务类型",
            terminologyIndex.labelsFor(MedicationCatalogImportTerminologyIndex.DICT_FIN_TYPE));
        this.writeOptionColumn(sheet, 11, "是/否",
            terminologyIndex.labelsFor(MedicationCatalogImportTerminologyIndex.DICT_YES_NO));
        this.writeOptionColumn(sheet, 12, "药品性质",
            terminologyIndex.labelsFor(MedicationCatalogImportTerminologyIndex.DICT_MEDICINE_PROPERTIES));
        this.writeOptionColumn(sheet, 13, "医保等级",
            terminologyIndex.labelsFor(MedicationCatalogImportTerminologyIndex.DICT_CHRGITM_LV));
        this.writeOptionColumn(sheet, 14, "病案结算类型",
            terminologyIndex.labelsFor(MedicationCatalogImportTerminologyIndex.DICT_MR_SETTLEMENT));
        this.writeOptionColumn(sheet, 15, "医保类别",
            terminologyIndex.labelsFor(MedicationCatalogImportTerminologyIndex.DICT_YB_TYPE));
        this.writeOptionColumn(sheet, 16, "用法说明",
            terminologyIndex.labelsFor(MedicationCatalogImportTerminologyIndex.DICT_DOSAGE_INSTRUCTION));
        this.writeOptionColumn(sheet, 17, "DDD单位",
            terminologyIndex.labelsFor(MedicationCatalogImportTerminologyIndex.DICT_DDD_UNIT));
        this.writeOptionColumn(sheet, 18, "拆分属性",
            terminologyIndex.labelsFor(MedicationCatalogImportTerminologyIndex.DICT_ORDER_CALC_RULE));
        this.createNamedRanges(workbook);
    }

    /**
     * 填写导入模板数据 Sheet：分组表头 + 字段表头 + 示例行 + 下拉。
     *
     * @param dataSheet 数据 Sheet
     * @param workbook  工作簿
     */
    private void fillDataSheet(XSSFSheet dataSheet, XSSFWorkbook workbook) {
        CellStyle requiredHeaderStyle = this.createHeaderStyle(workbook, IndexedColors.CORAL, true);
        CellStyle optionalHeaderStyle = this.createHeaderStyle(workbook, IndexedColors.GREY_25_PERCENT, false);
        CellStyle exampleStyle = this.createExampleStyle(workbook);
        Set<String> requiredSet = new HashSet<>(Arrays.asList(MedicationCatalogImportConstants.REQUIRED_HEADERS));
        Row groupRow = dataSheet.createRow(0);
        this.mergeAndLabel(dataSheet, groupRow, 0, 11, "基础信息");
        this.mergeAndLabel(dataSheet, groupRow, 12, 29, "临床与规格");
        this.mergeAndLabel(dataSheet, groupRow, 30, 45, "库存与单位（价格：元/包装单位）");
        this.mergeAndLabel(dataSheet, groupRow, 46, 55, "业务信息");
        Row headerRow = dataSheet.createRow(MedicationCatalogImportConstants.HEADER_ROW_INDEX);
        for (int col = 0; col < MedicationCatalogImportConstants.COLUMN_HEADERS.length; col++) {
            String header = MedicationCatalogImportConstants.COLUMN_HEADERS[col];
            Cell cell = headerRow.createCell(col);
            if (requiredSet.contains(header)) {
                cell.setCellValue("*" + header);
                cell.setCellStyle(requiredHeaderStyle);
            } else {
                cell.setCellValue(header);
                cell.setCellStyle(optionalHeaderStyle);
            }
        }
        this.fillExampleRow(dataSheet, 2, exampleStyle, this.exampleWestValues());
        this.fillExampleRow(dataSheet, 3, exampleStyle, this.examplePatentValues());
        this.addHeaderComments(dataSheet, workbook);
        this.applyColumnWidths(dataSheet);
        dataSheet.createFreezePane(1, 2);
        this.applyDropdownValidations(dataSheet);
    }

    /**
     * 写入导出行单元格。
     *
     * @param sheet         目标 Sheet
     * @param rowIndex      0-based 行号
     * @param values        列值；可短于 colCount（缺列留空）
     * @param colCount      写出列数（与表头一致）
     * @param numericColumns 须写数字的列下标（已按投影重映射）
     */
    private void fillDataRow(
        Sheet sheet,
        int rowIndex,
        String[] values,
        int colCount,
        Set<Integer> numericColumns) {
        Row row = sheet.createRow(rowIndex);
        if (values == null) {
            return;
        }
        for (int col = 0; col < colCount; col++) {
            Cell cell = row.createCell(col);
            if (col < values.length && values[col] != null) {
                CatalogExportNumericCellWriter.write(
                    cell, col, values[col], numericColumns);
            }
        }
    }

    /**
     * 兼容旧调用：全量 NUMERIC 列写出导出行。
     *
     * @param sheet    目标 Sheet
     * @param rowIndex 0-based 行号
     * @param values   列值
     * @param colCount 写出列数
     */
    private void fillDataRow(Sheet sheet, int rowIndex, String[] values, int colCount) {
        this.fillDataRow(sheet, rowIndex, values, colCount, EXPORT_NUMERIC_COLUMNS);
    }

    private String[] exampleWestValues() {
        String[] values = new String[MedicationCatalogImportConstants.COLUMN_HEADERS.length];
        values[0] = "[示例]阿莫西林胶囊";
        values[1] = "阿莫仙";
        values[2] = "西药";
        values[3] = "某某制药有限公司";
        values[4] = "西药费";
        values[12] = "化学药品";
        values[13] = "0.25g*24粒/盒";
        values[14] = "粒";
        values[15] = "1";
        values[16] = "胶囊剂";
        values[30] = "盒";
        values[31] = "片";
        values[32] = "24";
        values[33] = "盒";
        values[34] = "24";
        values[35] = "盒";
        values[36] = "24";
        values[37] = "片";
        values[38] = "1";
        values[39] = "10.5";
        values[40] = "15.8";
        values[42] = "拆零单位 - 单次取整（可拆）";
        values[43] = "拆零单位 - 单次取整（可拆）";
        return values;
    }

    private String[] examplePatentValues() {
        String[] values = new String[MedicationCatalogImportConstants.COLUMN_HEADERS.length];
        values[0] = "[示例]某某中成药";
        values[1] = "某某牌中成药";
        values[2] = "中成药";
        values[3] = "某某药业股份有限公司";
        values[4] = "中成药费";
        values[12] = "中成药";
        values[13] = "6g*10袋/盒";
        values[14] = "g";
        values[15] = "6";
        values[16] = "颗粒剂";
        values[30] = "盒";
        values[31] = "袋";
        values[32] = "10";
        values[33] = "盒";
        values[34] = "10";
        values[35] = "盒";
        values[36] = "10";
        values[37] = "袋";
        values[38] = "1";
        values[39] = "20";
        values[40] = "28";
        values[42] = "包装单位 - 整包不拆";
        values[43] = "包装单位 - 整包不拆";
        return values;
    }

    private void fillSysSheet(Sheet sysSheet) {
        Row row1 = sysSheet.createRow(0);
        row1.createCell(0).setCellValue("TEMPLATE_CODE");
        row1.createCell(1).setCellValue(MedicationCatalogImportConstants.TEMPLATE_CODE);
        Row row2 = sysSheet.createRow(1);
        row2.createCell(0).setCellValue("TEMPLATE_VERSION");
        row2.createCell(1).setCellValue(MedicationCatalogImportConstants.TEMPLATE_VERSION);
        Row row3 = sysSheet.createRow(2);
        row3.createCell(0).setCellValue("COLUMN_FINGERPRINT");
        row3.createCell(1).setCellValue(MedicationCatalogImportWorkbookParser.computeFingerprint());
    }

    private int writeOptionColumn(Sheet sheet, int colIndex, String title, List<String> labels) {
        Row titleRow = sheet.getRow(0);
        if (titleRow == null) {
            titleRow = sheet.createRow(0);
        }
        titleRow.createCell(colIndex).setCellValue(title);
        int rowIndex = MedicationCatalogImportConstants.OPTION_DATA_START_ROW_INDEX;
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
        if (rowIndex == MedicationCatalogImportConstants.OPTION_DATA_START_ROW_INDEX) {
            return MedicationCatalogImportConstants.OPTION_DATA_START_ROW_INDEX + 1;
        }
        return rowIndex;
    }

    private void createNamedRanges(XSSFWorkbook workbook) {
        String sheetName = MedicationCatalogImportConstants.SHEET_OPTIONS;
        this.createNamedRange(workbook, "OPT_CATEGORY", sheetName, 0,
            this.countLabels(MedicationCatalogImportTerminologyIndex.DICT_CATEGORY));
        this.createNamedRange(workbook, "OPT_DOSE_UNIT", sheetName, 1,
            this.countLabels(MedicationCatalogImportTerminologyIndex.DICT_DOSE_UNIT));
        // 四类单位命名区域均指向同源 DICT_UNIT（各列内容相同，Excel 校验名保留列语义）
        int unitEndRow = this.countLabels(MedicationCatalogImportTerminologyIndex.DICT_UNIT);
        this.createNamedRange(workbook, "OPT_PURCHASE_UNIT", sheetName, 2, unitEndRow);
        this.createNamedRange(workbook, "OPT_SPLIT_UNIT", sheetName, 3, unitEndRow);
        this.createNamedRange(workbook, "OPT_PACKAGE_UNIT", sheetName, 4, unitEndRow);
        this.createNamedRange(workbook, "OPT_STOCK_UNIT", sheetName, 5, unitEndRow);
        this.createNamedRange(workbook, "OPT_TRACE_UNIT", sheetName, 6, unitEndRow);
        this.createNamedRange(workbook, "OPT_DOSE_FORM", sheetName, 7,
            this.countLabels(MedicationCatalogImportTerminologyIndex.DICT_DOSE_FORM));
        this.createNamedRange(workbook, "OPT_METHOD", sheetName, 8,
            this.countLabels(MedicationCatalogImportTerminologyIndex.DICT_METHOD));
        this.createNamedRange(workbook, "OPT_RATE", sheetName, 9,
            this.countLabels(MedicationCatalogImportTerminologyIndex.DICT_RATE));
        this.createNamedRange(workbook, "OPT_FIN_TYPE", sheetName, 10,
            this.countLabels(MedicationCatalogImportTerminologyIndex.DICT_FIN_TYPE));
        this.createNamedRange(workbook, "OPT_YES_NO", sheetName, 11,
            this.countLabels(MedicationCatalogImportTerminologyIndex.DICT_YES_NO));
        this.createNamedRange(workbook, "OPT_MED_PROP", sheetName, 12,
            this.countLabels(MedicationCatalogImportTerminologyIndex.DICT_MEDICINE_PROPERTIES));
        this.createNamedRange(workbook, "OPT_CHRGITM_LV", sheetName, 13,
            this.countLabels(MedicationCatalogImportTerminologyIndex.DICT_CHRGITM_LV));
        this.createNamedRange(workbook, "OPT_MR_SETTLE", sheetName, 14,
            this.countLabels(MedicationCatalogImportTerminologyIndex.DICT_MR_SETTLEMENT));
        this.createNamedRange(workbook, "OPT_YB_TYPE", sheetName, 15,
            this.countLabels(MedicationCatalogImportTerminologyIndex.DICT_YB_TYPE));
        this.createNamedRange(workbook, "OPT_DOSAGE_INSTR", sheetName, 16,
            this.countLabels(MedicationCatalogImportTerminologyIndex.DICT_DOSAGE_INSTRUCTION));
        this.createNamedRange(workbook, "OPT_DDD_UNIT", sheetName, 17,
            this.countLabels(MedicationCatalogImportTerminologyIndex.DICT_DDD_UNIT));
        this.createNamedRange(workbook, "OPT_ORDER_CALC", sheetName, 18,
            this.countLabels(MedicationCatalogImportTerminologyIndex.DICT_ORDER_CALC_RULE));
    }

    private int countLabels(String dictKey) {
        List<String> labels = terminologyIndex.labelsFor(dictKey);
        if (labels == null || labels.isEmpty()) {
            return MedicationCatalogImportConstants.OPTION_DATA_START_ROW_INDEX + 1;
        }
        return MedicationCatalogImportConstants.OPTION_DATA_START_ROW_INDEX + labels.size();
    }

    private void createNamedRange(XSSFWorkbook workbook, String name, String sheetName, int colIndex, int endRowExcel) {
        Name namedRange = workbook.createName();
        namedRange.setNameName(name);
        String colLetter = this.columnLetter(colIndex);
        String refersTo = "'" + sheetName + "'!$" + colLetter + "$2:$" + colLetter + "$" + endRowExcel;
        namedRange.setRefersToFormula(refersTo);
    }

    private void applyDropdownValidations(XSSFSheet dataSheet) {
        int firstRow = MedicationCatalogImportConstants.DATA_START_ROW_INDEX;
        int lastRow = MedicationCatalogImportConstants.VALIDATION_END_ROW - 1;
        DataValidationHelper helper = dataSheet.getDataValidationHelper();
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 2, "OPT_CATEGORY", "请选择药品分类");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 4, "OPT_FIN_TYPE", "请选择财务类型");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 8, "OPT_CHRGITM_LV", "请选择医保等级");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 9, "OPT_MR_SETTLE", "请选择病案结算类型");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 10, "OPT_YES_NO", "请选择是否医保对码");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 11, "OPT_YB_TYPE", "请选择医保类别");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 12, "OPT_MED_PROP", "请选择药品性质");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 14, "OPT_DOSE_UNIT", "请选择剂量单位");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 16, "OPT_DOSE_FORM", "请选择剂型");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 17, "OPT_YES_NO", "请选择皮试判别");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 18, "OPT_YES_NO", "请选择输液标记");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 20, "OPT_METHOD", "请选择默认用法");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 21, "OPT_RATE", "请选择默认频次");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 25, "OPT_DOSAGE_INSTR", "请选择用法说明");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 29, "OPT_DDD_UNIT", "请选择DDD单位");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 30, "OPT_PURCHASE_UNIT", "请选择采购单位");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 31, "OPT_SPLIT_UNIT", "请选择拆零单位");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 33, "OPT_PACKAGE_UNIT", "请选择包装单位");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 35, "OPT_TRACE_UNIT", "请选择追溯码单位");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 37, "OPT_STOCK_UNIT", "请选择库存单位");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 42, "OPT_ORDER_CALC", "请选择门诊拆分属性");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 43, "OPT_ORDER_CALC", "请选择住院临时医嘱拆分属性");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 48, "OPT_YES_NO", "请选择基药标识");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 49, "OPT_YES_NO", "请选择儿童用药标志");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 50, "OPT_YES_NO", "请选择自制药标识");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 51, "OPT_YES_NO", "请选择限制使用");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 52, "OPT_YES_NO", "请选择抗生素");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 53, "OPT_YES_NO", "请选择处方药标志");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 54, "OPT_YES_NO", "请选择国家重点监控药品");
        this.addListValidation(dataSheet, helper, firstRow, lastRow, 55, "OPT_YES_NO", "请选择集采药品");
    }

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

    private CellStyle createExampleStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void mergeAndLabel(Sheet sheet, Row row, int fromCol, int toCol, String label) {
        if (fromCol < toCol) {
            sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), fromCol, toCol));
        }
        row.createCell(fromCol).setCellValue(label);
    }

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

    private void addHeaderComments(Sheet dataSheet, XSSFWorkbook workbook) {
        CreationHelper factory = workbook.getCreationHelper();
        Drawing<?> drawing = dataSheet.createDrawingPatriarch();
        this.putComment(dataSheet, drawing, factory, 13, "规格示例：0.25g*24粒/盒");
        this.putComment(dataSheet, drawing, factory, 15, "1个剂量单位相对拆零单位的个数，如拆零=粒且剂量=粒则填 1");
        this.putComment(dataSheet, drawing, factory, 18, "是/否；对应目录输液/注射标记 inject_flag");
        this.putComment(dataSheet, drawing, factory, 32, "1个采购单位=多少个拆零单位，例如 1盒=24片 则填 24");
        this.putComment(dataSheet, drawing, factory, 34, "1个包装单位=多少个拆零单位；常与采购/拆零倍数相同");
        this.putComment(dataSheet, drawing, factory, 35, "追溯码贴在哪个包装单位上（可与五类业务单位不同）");
        this.putComment(dataSheet, drawing, factory, 36, "1个追溯码单位=多少个拆零单位");
        this.putComment(dataSheet, drawing, factory, 38, "1个库存单位=多少个拆零单位；库存=拆零时填 1");
        this.putComment(dataSheet, drawing, factory, 39, "购入价：元/一个包装单位（不是拆零单价）");
        this.putComment(dataSheet, drawing, factory, 40, "零售价：元/一个包装单位（不是拆零单价）");
        this.putComment(dataSheet, drawing, factory, 44, "警戒线最低值，单位为库存单位");
        this.putComment(dataSheet, drawing, factory, 45, "警戒线最高值，单位为库存单位");
        this.putComment(dataSheet, drawing, factory, 46, "院内旧编码仅作备注，不作为系统编码；可与药品说明同时填写");
    }

    private void putComment(
        Sheet sheet,
        Drawing<?> drawing,
        CreationHelper factory,
        int colIndex,
        String text
    ) {
        Row headerRow = sheet.getRow(MedicationCatalogImportConstants.HEADER_ROW_INDEX);
        if (headerRow == null) {
            return;
        }
        Cell cell = headerRow.getCell(colIndex);
        if (cell == null) {
            return;
        }
        ClientAnchor anchor = factory.createClientAnchor();
        anchor.setCol1(colIndex);
        anchor.setRow1(MedicationCatalogImportConstants.HEADER_ROW_INDEX);
        Comment comment = drawing.createCellComment(anchor);
        comment.setString(factory.createRichTextString(text));
        cell.setCellComment(comment);
    }

    private void applyColumnWidths(Sheet dataSheet) {
        dataSheet.setColumnWidth(0, 6000);
        dataSheet.setColumnWidth(1, 4500);
        dataSheet.setColumnWidth(3, 7000);
        dataSheet.setColumnWidth(13, 5500);
        for (int col = 47; col <= 52; col++) {
            dataSheet.setColumnWidth(col, 3200);
        }
    }

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

