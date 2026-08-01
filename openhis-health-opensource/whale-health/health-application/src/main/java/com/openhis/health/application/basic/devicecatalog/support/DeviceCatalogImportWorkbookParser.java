/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.devicecatalog.support;

import com.openhis.health.domain.share.constants.DeviceCatalogImportConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * 耗材目录导入 xlsx 解析器：模板版本/列指纹 Gate 与数据行读取。
 */
@Component
@RequiredArgsConstructor
public class DeviceCatalogImportWorkbookParser {

    private static final String SYS_KEY_TEMPLATE_CODE = "TEMPLATE_CODE";
    private static final String SYS_KEY_TEMPLATE_VERSION = "TEMPLATE_VERSION";
    private static final String SYS_KEY_COLUMN_FINGERPRINT = "COLUMN_FINGERPRINT";

    private final DataFormatter dataFormatter = new DataFormatter();

    /**
     * 解析上传 workbook，通过 Gate 后返回数据行与原始字节副本。
     *
     * @param bytes xlsx 字节
     * @return 解析结果
     */
    public ParseResult parse(byte[] bytes) {
        // Gate：文件非空
        if (bytes == null || bytes.length == 0) {
            throw new ValidationException("导入耗材目录失败：文件不能为空");
        }
        // Gate：可打开为 xlsx
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes);
             XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            // Gate：必备 Sheet
            Sheet dataSheet = workbook.getSheet(DeviceCatalogImportConstants.SHEET_DATA);
            Sheet sysSheet = workbook.getSheet(DeviceCatalogImportConstants.SHEET_SYS);
            if (dataSheet == null || sysSheet == null) {
                throw new BusinessException(
                    DeviceCatalogImportConstants.ERR_SHEET_MISSING,
                    "导入耗材目录失败：缺少必备工作表，请重新下载模板");
            }
            // Gate：_sys 模板编码与版本
            this.assertSysMetadata(sysSheet);
            // Gate：表头与列指纹
            this.assertHeaderAndFingerprint(dataSheet, sysSheet);
            // Pre：自数据起始行读取有效行
            List<DeviceCatalogImportRow> rows = this.readDataRows(dataSheet);
            ParseResult result = new ParseResult();
            result.setRows(rows);
            result.setOriginalBytes(bytes);
            return result;
        } catch (IOException ex) {
            throw new BusinessException(
                DeviceCatalogImportConstants.ERR_FILE_OPEN,
                "导入耗材目录失败：无法打开 xlsx 文件",
                ex);
        }
    }

    /**
     * 计算列指纹：{@link DeviceCatalogImportConstants#COLUMN_HEADERS} 以 {@code |} 拼接后 SHA-256 小写 hex。
     *
     * @return 指纹字符串
     */
    public static String computeFingerprint() {
        String joined = String.join("|", DeviceCatalogImportConstants.COLUMN_HEADERS);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(joined.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new BusinessException(
                DeviceCatalogImportConstants.ERR_COLUMN_FINGERPRINT,
                "导入耗材目录失败：列指纹算法不可用",
                ex);
        }
    }

    /**
     * 校验 _sys 元数据：模板编码与版本须与常量一致。
     */
    private void assertSysMetadata(Sheet sysSheet) {
        String templateCode = this.readSysValue(sysSheet, SYS_KEY_TEMPLATE_CODE);
        String templateVersion = this.readSysValue(sysSheet, SYS_KEY_TEMPLATE_VERSION);
        if (!DeviceCatalogImportConstants.TEMPLATE_CODE.equals(templateCode)
            || !DeviceCatalogImportConstants.TEMPLATE_VERSION.equals(templateVersion)) {
            throw new BusinessException(
                DeviceCatalogImportConstants.ERR_TEMPLATE_VERSION,
                "导入耗材目录失败：模板编码或版本不匹配，请重新下载模板");
        }
    }

    /**
     * 校验表头列序与列指纹（任一失败即拒绝）。
     */
    private void assertHeaderAndFingerprint(Sheet dataSheet, Sheet sysSheet) {
        Row headerRow = dataSheet.getRow(DeviceCatalogImportConstants.HEADER_ROW_INDEX);
        if (headerRow == null) {
            throw new BusinessException(
                DeviceCatalogImportConstants.ERR_COLUMN_FINGERPRINT,
                "导入耗材目录失败：缺少字段表头行，请重新下载模板");
        }
        String[] actualHeaders = new String[DeviceCatalogImportConstants.COLUMN_HEADERS.length];
        for (int col = 0; col < DeviceCatalogImportConstants.COLUMN_HEADERS.length; col++) {
            String cellText = this.readCellAsString(headerRow.getCell(col));
            actualHeaders[col] = this.normalizeHeader(cellText);
        }
        boolean headerMatch = true;
        for (int i = 0; i < DeviceCatalogImportConstants.COLUMN_HEADERS.length; i++) {
            if (!DeviceCatalogImportConstants.COLUMN_HEADERS[i].equals(actualHeaders[i])) {
                headerMatch = false;
                break;
            }
        }
        String expectedFingerprint = DeviceCatalogImportWorkbookParser.computeFingerprint();
        String storedFingerprint = this.readSysValue(sysSheet, SYS_KEY_COLUMN_FINGERPRINT);
        boolean fingerprintMatch = expectedFingerprint.equals(storedFingerprint);
        if (!headerMatch || !fingerprintMatch) {
            throw new BusinessException(
                DeviceCatalogImportConstants.ERR_COLUMN_FINGERPRINT,
                "导入耗材目录失败：表头或列指纹不一致，请重新下载模板，勿修改表头");
        }
    }

    /**
     * 自 {@link DeviceCatalogImportConstants#DATA_START_ROW_INDEX} 起读取数据行。
     */
    private List<DeviceCatalogImportRow> readDataRows(Sheet dataSheet) {
        List<DeviceCatalogImportRow> rows = new ArrayList<>();
        int lastRow = dataSheet.getLastRowNum();
        for (int rowIndex = DeviceCatalogImportConstants.DATA_START_ROW_INDEX; rowIndex <= lastRow; rowIndex++) {
            Row row = dataSheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            String name = this.readCellAsString(row.getCell(0));
            if (StringUtils.isEmpty(name)) {
                continue;
            }
            String trimmedName = name.trim();
            if (trimmedName.startsWith(DeviceCatalogImportConstants.EXAMPLE_NAME_PREFIX)) {
                continue;
            }
            DeviceCatalogImportRow importRow = new DeviceCatalogImportRow();
            importRow.setExcelRowNum(rowIndex + 1);
            // A 基础信息 0～12（已去掉医药机构目录编码列）
            importRow.setName(trimmedName);
            importRow.setMerchandiseName(this.readCellAsString(row.getCell(1)));
            importRow.setCategory(this.readCellAsString(row.getCell(2)));
            importRow.setDeviceType(this.readCellAsString(row.getCell(3)));
            importRow.setManufacturerText(this.readCellAsString(row.getCell(4)));
            importRow.setFinType(this.readCellAsString(row.getCell(5)));
            importRow.setTotalVolume(this.readCellAsString(row.getCell(6)));
            importRow.setApprovalNumber(this.readCellAsString(row.getCell(7)));
            importRow.setYbNo(this.readCellAsString(row.getCell(8)));
            importRow.setChrgitmLvText(this.readCellAsString(row.getCell(9)));
            importRow.setMrSettlementType(this.readCellAsString(row.getCell(10)));
            importRow.setYbMatchFlagText(this.readCellAsString(row.getCell(11)));
            importRow.setYbType(this.readCellAsString(row.getCell(12)));
            // B 库存与单位 13～24
            importRow.setPurchaseUnit(this.readCellAsString(row.getCell(13)));
            importRow.setSplitUnit(this.readCellAsString(row.getCell(14)));
            importRow.setPurchaseUnitRatioText(this.readCellAsString(row.getCell(15)));
            importRow.setPackageUnit(this.readCellAsString(row.getCell(16)));
            importRow.setPackageUnitRatioText(this.readCellAsString(row.getCell(17)));
            importRow.setStockUnit(this.readCellAsString(row.getCell(18)));
            importRow.setStockUnitRatioText(this.readCellAsString(row.getCell(19)));
            importRow.setPurchasePriceText(this.readCellAsString(row.getCell(20)));
            importRow.setRetailPriceText(this.readCellAsString(row.getCell(21)));
            importRow.setMaximumRetailPriceText(this.readCellAsString(row.getCell(22)));
            importRow.setItemMinQuantityText(this.readCellAsString(row.getCell(23)));
            importRow.setItemMaxQuantityText(this.readCellAsString(row.getCell(24)));
            // C 业务信息 25～31
            importRow.setLegacyCode(this.readCellAsString(row.getCell(25)));
            importRow.setModelNumber(this.readCellAsString(row.getCell(26)));
            importRow.setAllergenFlagText(this.readCellAsString(row.getCell(27)));
            importRow.setYbFlagText(this.readCellAsString(row.getCell(28)));
            importRow.setHvcmFlagText(this.readCellAsString(row.getCell(29)));
            importRow.setChargeableFlagText(this.readCellAsString(row.getCell(30)));
            importRow.setDescriptionText(this.readCellAsString(row.getCell(31)));
            rows.add(importRow);
        }
        return rows;
    }

    /**
     * 读取 _sys 表 A 列键名对应 B 列值。
     */
    private String readSysValue(Sheet sysSheet, String key) {
        int lastRow = sysSheet.getLastRowNum();
        for (int rowIndex = 0; rowIndex <= lastRow; rowIndex++) {
            Row row = sysSheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            String cellKey = this.readCellAsString(row.getCell(0));
            if (key.equals(cellKey)) {
                return this.readCellAsString(row.getCell(1));
            }
        }
        return null;
    }

    /**
     * 表头规范化：trim 并去掉前导 {@code *}。
     */
    private String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }
        String trimmed = header.trim();
        if (trimmed.startsWith("*")) {
            return trimmed.substring(1).trim();
        }
        return trimmed;
    }

    /**
     * 读取单元格为字符串：文本 trim；数值按 plain string（无科学计数）。
     */
    private String readCellAsString(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return dataFormatter.formatCellValue(cell).trim();
        }
        String text = dataFormatter.formatCellValue(cell);
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    /**
     * 解析结果：数据行与原始 workbook 字节。
     */
    @Getter
    public static class ParseResult {

        /** 有效导入行（已跳过空行与示例行）。 */
        private List<DeviceCatalogImportRow> rows = new ArrayList<>();

        /** 原始上传字节（错误标注文件基底）。 */
        private byte[] originalBytes;

        /**
         * 设置数据行。
         *
         * @param rows 数据行
         */
        public void setRows(List<DeviceCatalogImportRow> rows) {
            if (rows == null) {
                this.rows = new ArrayList<>();
                return;
            }
            this.rows = rows;
        }

        /**
         * 设置原始字节。
         *
         * @param originalBytes 原始上传字节
         */
        public void setOriginalBytes(byte[] originalBytes) {
            this.originalBytes = originalBytes;
        }
    }
}
