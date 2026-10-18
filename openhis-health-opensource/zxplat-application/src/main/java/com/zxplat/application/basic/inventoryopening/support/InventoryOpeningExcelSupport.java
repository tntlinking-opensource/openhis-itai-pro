/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.inventoryopening.support;

import com.zxplat.application.basic.inventoryopening.dto.InventoryOpeningImportErrorDto;
import com.zxplat.application.basic.inventoryopening.dto.InventoryOpeningLineDto;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 库存期初 Excel 模板生成与解析（IF-BAS-INV-OPN-12/13 · 整单失败零写入）。
 *
 * <p>模板按业务编码填写（物品编码、供应商编码），导入后再解析为主键；禁止要求用户填库内 id。</p>
 */
@Component
public class InventoryOpeningExcelSupport {

    /** 模板文件名。 */
    public static final String TEMPLATE_FILE_NAME = "库存期初导入模板.xlsx";
    /** MIME。 */
    public static final String XLSX_CONTENT_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /**
     * 明细表头（业务编码口径，不含主键列）。
     *
     * <pre>
     * 0物品类型 1物品编码 2批号 3效期 4生产日期 5数量 6单位编码
     * 7进价 8售价 9行金额 10追溯码 11供应商编码 12供应商名称
     * </pre>
     */
    private static final String[] HEADERS = {
        "物品类型", "物品编码", "批号", "效期", "生产日期", "数量", "单位编码",
        "进价", "售价", "行金额", "追溯码", "供应商编码", "供应商名称"
    };

    private final DataFormatter dataFormatter = new DataFormatter();

    /**
     * 生成 xlsx 模板：明细 sheet + 填写说明 sheet。
     *
     * @return 模板字节
     */
    public byte[] buildTemplateBytes() {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("期初明细");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
                sheet.setColumnWidth(i, 16 * 256);
            }
            // 示例行（导入前请删或改成真实数据）
            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue("药品");
            sample.createCell(1).setCellValue("示例物品编码");
            sample.createCell(2).setCellValue("批号示例");
            sample.createCell(3).setCellValue("2027-12-31");
            sample.createCell(5).setCellValue("10");
            sample.createCell(7).setCellValue("1.00");
            sample.createCell(11).setCellValue("示例供应商编码");

            Sheet guide = workbook.createSheet("填写说明");
            String[] tips = {
                "1. 在「期初明细」填写数据；导入前请删除或改写示例行。",
                "2. 物品类型：填「药品」或「耗材」（勿填表名/主键）。",
                "3. 物品编码：药品/耗材目录中的业务编码（busNo），必填。",
                "4. 批号、效期、数量必填；生产日期可选；效期格式 yyyy-MM-dd。",
                "5. 数量与单位编码：数量按您填写的「单位编码」理解；空单位按期初页默认库存单位（STOCK）理解；系统按该单位换算为拆零小数量入账。单位仅用于数量，不是计价单位。",
                "6. 进价：一律填「包装单位」价格（与数量单位无关）。药库期初必填；药房/耗材房可空。行金额可选。「售价」列已废弃，填写将被忽略（同意时批号售价=进价；中药另由产品自动加价）。",
                "7. 追溯码：多个用英文逗号分隔；西药/中成药允许不扫码（无码批）；有码时贴码单位取目录（trace_unit），应扫码数=ceil(行拆零/每码拆零)，半盒允许。",
                "8. 供应商：优先填「供应商编码」；无编码时可填「供应商名称」（须唯一精确匹配）。",
                "9. 任一行校验失败则整单失败、不生成草稿（与页面保存同一套业务闸门）。",
                "10. 目标库房在系统导入弹窗中选择，无需在 Excel 中填写。"
            };
            for (int i = 0; i < tips.length; i++) {
                guide.createRow(i).createCell(0).setCellValue(tips[i]);
            }
            guide.setColumnWidth(0, 90 * 256);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_105, "生成期初导入模板失败");
        }
    }

    /**
     * 解析上传 xlsx；单元格级错误写入 errors；成功行暂存业务编码，待应用层解析主键。
     *
     * @param fileBytes 文件字节
     * @return 解析结果（rawLines + errors）
     */
    public ParseResult parse(byte[] fileBytes) {
        ParseResult result = new ParseResult();
        if (fileBytes == null || fileBytes.length == 0) {
            InventoryOpeningImportErrorDto err = new InventoryOpeningImportErrorDto();
            err.setExcelRowNum(Integer.valueOf(0));
            err.setColumnName("文件");
            err.setCode(ErrorCodeConstants.BAS_INV_OPN_012);
            err.setMessage("上传文件为空");
            result.getErrors().add(err);
            return result;
        }
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(fileBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                InventoryOpeningImportErrorDto err = new InventoryOpeningImportErrorDto();
                err.setExcelRowNum(Integer.valueOf(0));
                err.setColumnName("工作表");
                err.setCode(ErrorCodeConstants.BAS_INV_OPN_012);
                err.setMessage("工作表为空");
                result.getErrors().add(err);
                return result;
            }
            int lastRow = sheet.getLastRowNum();
            for (int r = 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null || this.isBlankRow(row)) {
                    continue;
                }
                int excelRowNum = r + 1;
                try {
                    String itemTypeRaw = this.cellString(row, 0);
                    String itemBusNo = this.cellString(row, 1);
                    String supplierBusNo = this.cellString(row, 11);
                    // 跳过未改的示例行
                    if ("示例物品编码".equals(itemBusNo)
                        || "示例供应商编码".equals(supplierBusNo)) {
                        continue;
                    }
                    if (StringUtils.isEmpty(itemBusNo)) {
                        this.addError(result, excelRowNum, "物品编码", "不能为空");
                    }
                    InventoryOpeningLineDto line = new InventoryOpeningLineDto();
                    line.setItemTable(this.normalizeItemTable(itemTypeRaw));
                    if (StringUtils.isEmpty(line.getItemTable())) {
                        this.addError(result, excelRowNum, "物品类型", "须为「药品」或「耗材」");
                    }
                    line.setLotNumber(this.cellString(row, 2));
                    line.setEndTime(this.cellDateTime(row, 3, excelRowNum, "效期", result));
                    line.setStartTime(this.cellDateTimeOptional(row, 4));
                    line.setItemQuantity(this.cellDecimal(row, 5, excelRowNum, "数量", result));
                    line.setUnitCode(this.cellString(row, 6));
                    line.setPrice(this.cellDecimalOptional(row, 7));
                    // 第 8 列「售价」已废弃：兼容旧模板列位，不落库
                    line.setTotalPrice(this.cellDecimalOptional(row, 9));
                    line.setTraceNo(this.cellString(row, 10));
                    // 贴码单位已改由药典维护，导入不再读取行上追溯码单位

                    RawImportLine raw = new RawImportLine();
                    raw.setExcelRowNum(excelRowNum);
                    raw.setItemBusNo(itemBusNo);
                    raw.setSupplierBusNo(supplierBusNo);
                    raw.setSupplierName(this.cellString(row, 12));
                    raw.setLine(line);
                    if (StringUtils.isEmpty(raw.getSupplierBusNo()) && StringUtils.isEmpty(raw.getSupplierName())) {
                        this.addError(result, excelRowNum, "供应商", "请填写供应商编码或供应商名称");
                    }
                    result.getRawLines().add(raw);
                } catch (RuntimeException ex) {
                    InventoryOpeningImportErrorDto err = new InventoryOpeningImportErrorDto();
                    err.setExcelRowNum(Integer.valueOf(excelRowNum));
                    err.setColumnName("行");
                    err.setCode(ErrorCodeConstants.BAS_INV_OPN_012);
                    err.setMessage("解析失败：" + ex.getMessage());
                    result.getErrors().add(err);
                }
            }
            if (result.getRawLines().isEmpty() && result.getErrors().isEmpty()) {
                InventoryOpeningImportErrorDto err = new InventoryOpeningImportErrorDto();
                err.setExcelRowNum(Integer.valueOf(0));
                err.setColumnName("明细");
                err.setCode(ErrorCodeConstants.BAS_INV_OPN_012);
                err.setMessage("导入文件无有效明细行");
                result.getErrors().add(err);
            }
            return result;
        } catch (IOException ex) {
            InventoryOpeningImportErrorDto err = new InventoryOpeningImportErrorDto();
            err.setExcelRowNum(Integer.valueOf(0));
            err.setColumnName("文件");
            err.setCode(ErrorCodeConstants.BAS_INV_OPN_012);
            err.setMessage("无法读取 Excel：" + ex.getMessage());
            result.getErrors().add(err);
            return result;
        }
    }

    /**
     * 将模板中的中文/英文别名规范为定义表常量；无法识别则返回 null。
     *
     * @param raw 单元格原文
     * @return 表名常量或 null
     */
    private String normalizeItemTable(String raw) {
        if (StringUtils.isEmpty(raw)) {
            return null;
        }
        String t = raw.trim();
        if ("药品".equals(t) || "medication".equalsIgnoreCase(t)
            || TableNameConstants.MED_MEDICATION_DEFINITION.equals(t)) {
            return TableNameConstants.MED_MEDICATION_DEFINITION;
        }
        if ("耗材".equals(t) || "器材".equals(t) || "device".equalsIgnoreCase(t)
            || TableNameConstants.ADM_DEVICE_DEFINITION.equals(t)) {
            return TableNameConstants.ADM_DEVICE_DEFINITION;
        }
        return null;
    }

    /**
     * 判断模板列范围内是否全空（跳过空行）。
     *
     * @param row Excel 行
     * @return 全空时 true
     */
    private boolean isBlankRow(Row row) {
        for (int c = 0; c < HEADERS.length; c++) {
            String v = this.cellString(row, c);
            if (StringUtils.isNotEmpty(v)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 读取单元格为去空白字符串；空则 null。
     *
     * @param row 行
     * @param col 列下标
     * @return 文本或 null
     */
    private String cellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        String v = this.dataFormatter.formatCellValue(cell);
        if (v == null) {
            return null;
        }
        String trimmed = v.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    /**
     * 必填 BigDecimal：空或非数字写入错误清单。
     *
     * @param row          行
     * @param col          列
     * @param excelRowNum  行号
     * @param colName      列名
     * @param result       解析结果
     * @return 值或 null
     */
    private BigDecimal cellDecimal(Row row, int col, int excelRowNum, String colName, ParseResult result) {
        String raw = this.cellString(row, col);
        if (StringUtils.isEmpty(raw)) {
            this.addError(result, excelRowNum, colName, "不能为空");
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            this.addError(result, excelRowNum, colName, "须为数字");
            return null;
        }
    }

    /**
     * 可选 BigDecimal：空或非法返回 null。
     *
     * @param row 行
     * @param col 列
     * @return 值或 null
     */
    private BigDecimal cellDecimalOptional(Row row, int col) {
        String raw = this.cellString(row, col);
        if (StringUtils.isEmpty(raw)) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 必填日期时间：解析失败则记错误。
     *
     * @param row          行
     * @param col          列
     * @param excelRowNum  行号
     * @param colName      列名
     * @param result       解析结果
     * @return 值或 null
     */
    private LocalDateTime cellDateTime(Row row, int col, int excelRowNum, String colName, ParseResult result) {
        LocalDateTime v = this.cellDateTimeOptional(row, col);
        if (v == null) {
            this.addError(result, excelRowNum, colName, "不能为空");
        }
        return v;
    }

    /**
     * 可选日期时间：支持 Excel 日期单元格、{@code yyyy-MM-dd}、ISO 本地时间。
     *
     * @param row 行
     * @param col 列
     * @return 值或 null
     */
    private LocalDateTime cellDateTimeOptional(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            Date d = cell.getDateCellValue();
            if (d == null) {
                return null;
            }
            return LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
        }
        String raw = this.cellString(row, col);
        if (StringUtils.isEmpty(raw)) {
            return null;
        }
        try {
            if (raw.length() <= 10) {
                return LocalDate.parse(raw).atStartOfDay();
            }
            return LocalDateTime.parse(raw.replace(" ", "T"));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * 向解析结果追加一行导入错误（统一码 BAS_INV_OPN_012）。
     *
     * @param result       解析结果
     * @param excelRowNum  Excel 行号
     * @param colName      列名
     * @param message      说明
     */
    private void addError(ParseResult result, int excelRowNum, String colName, String message) {
        InventoryOpeningImportErrorDto err = new InventoryOpeningImportErrorDto();
        err.setExcelRowNum(Integer.valueOf(excelRowNum));
        err.setColumnName(colName);
        err.setCode(ErrorCodeConstants.BAS_INV_OPN_012);
        err.setMessage(message);
        result.getErrors().add(err);
    }

    /**
     * Excel 解析出的原始行（含业务编码，主键待解析）。
     */
    public static class RawImportLine {
        private int excelRowNum;
        private String itemBusNo;
        private String supplierBusNo;
        private String supplierName;
        private InventoryOpeningLineDto line;

        /** @return Excel 行号（1-based） */
        public int getExcelRowNum() {
            return excelRowNum;
        }

        public void setExcelRowNum(int excelRowNum) {
            this.excelRowNum = excelRowNum;
        }

        /** @return 物品业务编码 */
        public String getItemBusNo() {
            return itemBusNo;
        }

        public void setItemBusNo(String itemBusNo) {
            this.itemBusNo = itemBusNo;
        }

        /** @return 供应商业务编码 */
        public String getSupplierBusNo() {
            return supplierBusNo;
        }

        public void setSupplierBusNo(String supplierBusNo) {
            this.supplierBusNo = supplierBusNo;
        }

        /** @return 供应商名称（编码优先） */
        public String getSupplierName() {
            return supplierName;
        }

        public void setSupplierName(String supplierName) {
            this.supplierName = supplierName;
        }

        /** @return 待补齐主键的明细行 */
        public InventoryOpeningLineDto getLine() {
            return line;
        }

        public void setLine(InventoryOpeningLineDto line) {
            this.line = line;
        }
    }

    /**
     * Excel 解析结果。
     */
    public static class ParseResult {
        private final List<RawImportLine> rawLines = new ArrayList<>();
        private final List<InventoryOpeningImportErrorDto> errors = new ArrayList<>();

        /**
         * @return 解析出的原始行（含编码）
         */
        public List<RawImportLine> getRawLines() {
            return rawLines;
        }

        /**
         * @return 解析错误
         */
        public List<InventoryOpeningImportErrorDto> getErrors() {
            return errors;
        }

        /**
         * @return 有错误时 true
         */
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }
}
