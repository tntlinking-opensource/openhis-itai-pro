/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.medicationcatalog.support;

import com.openhis.health.domain.share.constants.MedicationCatalogImportConstants;
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
 * 药品目录导入 xlsx 解析器：模板版本/列指纹 Gate 与数据行读取。
 */
@Component
@RequiredArgsConstructor
public class MedicationCatalogImportWorkbookParser {

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
            throw new ValidationException("导入药品目录失败：文件不能为空");
        }
        // Gate：可打开为 xlsx
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes);
             XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            // Gate：必备 Sheet
            Sheet dataSheet = workbook.getSheet(MedicationCatalogImportConstants.SHEET_DATA);
            Sheet sysSheet = workbook.getSheet(MedicationCatalogImportConstants.SHEET_SYS);
            if (dataSheet == null || sysSheet == null) {
                throw new BusinessException(
                    MedicationCatalogImportConstants.ERR_SHEET_MISSING,
                    "导入药品目录失败：缺少必备工作表，请重新下载模板");
            }
            // Gate：_sys 模板编码与版本
            String templateVersion = this.assertSysMetadata(sysSheet);
            // Gate：表头与列指纹（按版本选用期望表头）
            this.assertHeaderAndFingerprint(dataSheet, sysSheet, templateVersion);
            // Pre：自数据起始行读取有效行（v1.4/v1.5 无追溯码列时回落包装单位）
            List<MedicationCatalogImportRow> rows = this.readDataRows(dataSheet, templateVersion);
            ParseResult result = new ParseResult();
            result.setRows(rows);
            result.setOriginalBytes(bytes);
            return result;
        } catch (IOException ex) {
            throw new BusinessException(
                MedicationCatalogImportConstants.ERR_FILE_OPEN,
                "导入药品目录失败：无法打开 xlsx 文件",
                ex);
        }
    }

    /**
     * 计算列指纹：{@link MedicationCatalogImportConstants#COLUMN_HEADERS} 以 {@code |} 拼接后 SHA-256 小写 hex。
     *
     * @return 指纹字符串
     */
    public static String computeFingerprint() {
        return MedicationCatalogImportWorkbookParser.computeFingerprintForHeaders(
            MedicationCatalogImportConstants.COLUMN_HEADERS);
    }

    /**
     * v1.5 旧表头指纹（54→52：无追溯码两列，表头「输液标记」）。
     *
     * @return 指纹字符串
     */
    public static String computeLegacyV15Fingerprint() {
        return MedicationCatalogImportWorkbookParser.computeFingerprintForHeaders(
            MedicationCatalogImportWorkbookParser.headersWithoutTrace(false));
    }

    /**
     * v1.4 旧表头指纹（无追溯码两列，「输液标记」列为「注射药品」）。
     *
     * @return 指纹字符串
     */
    public static String computeLegacyV14Fingerprint() {
        return MedicationCatalogImportWorkbookParser.computeFingerprintForHeaders(
            MedicationCatalogImportWorkbookParser.headersWithoutTrace(true));
    }

    /**
     * 构造无追溯码列的 52 列表头（v1.4/v1.5）。
     *
     * @param injectLegacyName true 时第 18 列用「注射药品」
     * @return 表头数组
     */
    public static String[] headersWithoutTrace(boolean injectLegacyName) {
        String[] full = MedicationCatalogImportConstants.COLUMN_HEADERS;
        // v1.7 末两列为新增标识；构造无追溯码表头时先回到 v1.6（54 列）再去掉追溯两列
        String[] current = java.util.Arrays.copyOf(full, full.length - 2);
        String[] legacy = new String[current.length - 2];
        int out = 0;
        for (int i = 0; i < current.length; i++) {
            if (i == MedicationCatalogImportConstants.COL_TRACE_UNIT
                || i == MedicationCatalogImportConstants.COL_TRACE_RATIO) {
                continue;
            }
            String header = current[i];
            if (injectLegacyName && i == MedicationCatalogImportConstants.COL_INJECT_FLAG) {
                header = MedicationCatalogImportConstants.HEADER_INJECT_FLAG_LEGACY;
            }
            legacy[out++] = header;
        }
        return legacy;
    }

    /**
     * 按给定表头数组计算 SHA-256 列指纹。
     *
     * @param headers 表头规范名
     * @return 指纹小写 hex
     */
    public static String computeFingerprintForHeaders(String[] headers) {
        String joined = String.join("|", headers);
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
                MedicationCatalogImportConstants.ERR_COLUMN_FINGERPRINT,
                "导入药品目录失败：列指纹算法不可用",
                ex);
        }
    }

    /**
     * 校验 _sys 元数据：模板编码须一致；版本允许 v1.6 / v1.5 / v1.4。
     *
     * @return 模板版本字面量
     */
    private String assertSysMetadata(Sheet sysSheet) {
        String templateCode = this.readSysValue(sysSheet, SYS_KEY_TEMPLATE_CODE);
        String templateVersion = this.readSysValue(sysSheet, SYS_KEY_TEMPLATE_VERSION);
        boolean versionOk = MedicationCatalogImportConstants.TEMPLATE_VERSION.equals(templateVersion)
            || MedicationCatalogImportConstants.TEMPLATE_VERSION_V16.equals(templateVersion)
            || MedicationCatalogImportConstants.TEMPLATE_VERSION_V15.equals(templateVersion)
            || MedicationCatalogImportConstants.TEMPLATE_VERSION_V14.equals(templateVersion);
        if (!MedicationCatalogImportConstants.TEMPLATE_CODE.equals(templateCode) || !versionOk) {
            throw new BusinessException(
                MedicationCatalogImportConstants.ERR_TEMPLATE_VERSION,
                "导入药品目录失败：模板编码或版本不匹配，请重新下载模板");
        }
        return templateVersion;
    }

    /**
     * 校验表头列序与列指纹（按版本选用 54 列或 52 列期望表头）。
     */
    private void assertHeaderAndFingerprint(Sheet dataSheet, Sheet sysSheet, String templateVersion) {
        Row headerRow = dataSheet.getRow(MedicationCatalogImportConstants.HEADER_ROW_INDEX);
        if (headerRow == null) {
            throw new BusinessException(
                MedicationCatalogImportConstants.ERR_COLUMN_FINGERPRINT,
                "导入药品目录失败：缺少字段表头行，请重新下载模板");
        }
        String[] expectedHeaders = this.expectedHeadersForVersion(templateVersion);
        String[] actualHeaders = new String[expectedHeaders.length];
        for (int col = 0; col < expectedHeaders.length; col++) {
            String cellText = this.readCellAsString(headerRow.getCell(col));
            actualHeaders[col] = this.normalizeHeader(cellText);
        }
        boolean headerMatch = this.headersMatch(expectedHeaders, actualHeaders);
        String storedFingerprint = this.readSysValue(sysSheet, SYS_KEY_COLUMN_FINGERPRINT);
        String expectedFingerprint =
            MedicationCatalogImportWorkbookParser.computeFingerprintForHeaders(expectedHeaders);
        boolean fingerprintMatch = expectedFingerprint.equals(storedFingerprint);
        if (!headerMatch || !fingerprintMatch) {
            throw new BusinessException(
                MedicationCatalogImportConstants.ERR_COLUMN_FINGERPRINT,
                "导入药品目录失败：表头或列指纹不一致，请重新下载模板，勿修改表头");
        }
    }

    /**
     * 按模板版本返回期望表头。
     */
    private String[] expectedHeadersForVersion(String templateVersion) {
        if (MedicationCatalogImportConstants.TEMPLATE_VERSION_V14.equals(templateVersion)) {
            return MedicationCatalogImportWorkbookParser.headersWithoutTrace(true);
        }
        if (MedicationCatalogImportConstants.TEMPLATE_VERSION_V15.equals(templateVersion)) {
            return MedicationCatalogImportWorkbookParser.headersWithoutTrace(false);
        }
        if (MedicationCatalogImportConstants.TEMPLATE_VERSION_V16.equals(templateVersion)) {
            String[] full = MedicationCatalogImportConstants.COLUMN_HEADERS;
            return java.util.Arrays.copyOf(full, full.length - 2);
        }
        return MedicationCatalogImportConstants.COLUMN_HEADERS;
    }

    /**
     * 表头与期望列逐列比对。
     */
    private boolean headersMatch(String[] expectedHeaders, String[] actualHeaders) {
        if (expectedHeaders.length != actualHeaders.length) {
            return false;
        }
        for (int i = 0; i < expectedHeaders.length; i++) {
            if (!expectedHeaders[i].equals(actualHeaders[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * 自 {@link MedicationCatalogImportConstants#DATA_START_ROW_INDEX} 起读取数据行。
     *
     * @param templateVersion 模板版本（决定列位移）
     */
    private List<MedicationCatalogImportRow> readDataRows(Sheet dataSheet, String templateVersion) {
        boolean hasTraceColumns = MedicationCatalogImportConstants.TEMPLATE_VERSION.equals(templateVersion)
            || MedicationCatalogImportConstants.TEMPLATE_VERSION_V16.equals(templateVersion);
        boolean hasNewFlags = MedicationCatalogImportConstants.TEMPLATE_VERSION.equals(templateVersion);
        List<MedicationCatalogImportRow> rows = new ArrayList<>();
        int lastRow = dataSheet.getLastRowNum();
        for (int rowIndex = MedicationCatalogImportConstants.DATA_START_ROW_INDEX; rowIndex <= lastRow; rowIndex++) {
            Row row = dataSheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            String name = this.readCellAsString(row.getCell(0));
            if (StringUtils.isEmpty(name)) {
                continue;
            }
            String trimmedName = name.trim();
            if (trimmedName.startsWith(MedicationCatalogImportConstants.EXAMPLE_NAME_PREFIX)) {
                continue;
            }
            MedicationCatalogImportRow importRow = new MedicationCatalogImportRow();
            importRow.setExcelRowNum(rowIndex + 1);
            // A 基础信息 0～11
            importRow.setName(trimmedName);
            importRow.setMerchandiseName(this.readCellAsString(row.getCell(1)));
            importRow.setCategory(this.readCellAsString(row.getCell(2)));
            importRow.setManufacturerText(this.readCellAsString(row.getCell(3)));
            importRow.setFinType(this.readCellAsString(row.getCell(4)));
            importRow.setApprovalNumber(this.readCellAsString(row.getCell(5)));
            importRow.setNationalDrugCode(this.readCellAsString(row.getCell(6)));
            importRow.setYbNo(this.readCellAsString(row.getCell(7)));
            importRow.setChrgitmLvText(this.readCellAsString(row.getCell(8)));
            importRow.setMrSettlementType(this.readCellAsString(row.getCell(9)));
            importRow.setYbMatchFlagText(this.readCellAsString(row.getCell(10)));
            importRow.setYbType(this.readCellAsString(row.getCell(11)));
            // B 临床与规格 12～29
            importRow.setPharmacologyCategory(this.readCellAsString(row.getCell(12)));
            importRow.setTotalVolume(this.readCellAsString(row.getCell(13)));
            importRow.setDoseUnit(this.readCellAsString(row.getCell(14)));
            importRow.setDoseUnitRatioText(this.readCellAsString(row.getCell(15)));
            importRow.setDoseForm(this.readCellAsString(row.getCell(16)));
            importRow.setSkinTestFlagText(this.readCellAsString(row.getCell(17)));
            importRow.setInjectFlagText(this.readCellAsString(row.getCell(18)));
            importRow.setUsageLimitText(this.readCellAsString(row.getCell(19)));
            importRow.setMethod(this.readCellAsString(row.getCell(20)));
            importRow.setRate(this.readCellAsString(row.getCell(21)));
            importRow.setDoseText(this.readCellAsString(row.getCell(22)));
            importRow.setMinUnitText(this.readCellAsString(row.getCell(23)));
            importRow.setMaxUnitText(this.readCellAsString(row.getCell(24)));
            importRow.setDosageInstruction(this.readCellAsString(row.getCell(25)));
            importRow.setRestrictedScope(this.readCellAsString(row.getCell(26)));
            importRow.setDefinitionText(this.readCellAsString(row.getCell(27)));
            importRow.setDddCodeText(this.readCellAsString(row.getCell(28)));
            importRow.setDddUnit(this.readCellAsString(row.getCell(29)));
            // C 库存与单位：v1.6 含追溯码两列；旧版紧接包装后为库存
            importRow.setPurchaseUnit(this.readCellAsString(row.getCell(30)));
            importRow.setSplitUnit(this.readCellAsString(row.getCell(31)));
            importRow.setPurchaseUnitRatioText(this.readCellAsString(row.getCell(32)));
            importRow.setPackageUnit(this.readCellAsString(row.getCell(33)));
            importRow.setPackageUnitRatioText(this.readCellAsString(row.getCell(34)));
            int col = 35;
            if (hasTraceColumns) {
                importRow.setTraceUnit(this.readCellAsString(row.getCell(col++)));
                importRow.setTraceUnitRatioText(this.readCellAsString(row.getCell(col++)));
            } else {
                // 旧模板无追溯码列：与包装单位同阶，便于必填与写入一致
                importRow.setTraceUnit(importRow.getPackageUnit());
                importRow.setTraceUnitRatioText(importRow.getPackageUnitRatioText());
            }
            importRow.setStockUnit(this.readCellAsString(row.getCell(col++)));
            importRow.setStockUnitRatioText(this.readCellAsString(row.getCell(col++)));
            importRow.setPurchasePriceText(this.readCellAsString(row.getCell(col++)));
            importRow.setRetailPriceText(this.readCellAsString(row.getCell(col++)));
            importRow.setMaximumRetailPriceText(this.readCellAsString(row.getCell(col++)));
            importRow.setPartAttribute(this.readCellAsString(row.getCell(col++)));
            importRow.setThoPartAttribute(this.readCellAsString(row.getCell(col++)));
            importRow.setItemMinQuantityText(this.readCellAsString(row.getCell(col++)));
            importRow.setItemMaxQuantityText(this.readCellAsString(row.getCell(col++)));
            // D 业务信息
            importRow.setLegacyCode(this.readCellAsString(row.getCell(col++)));
            importRow.setDrug69Code(this.readCellAsString(row.getCell(col++)));
            importRow.setBasicFlagText(this.readCellAsString(row.getCell(col++)));
            importRow.setChildrenFlagText(this.readCellAsString(row.getCell(col++)));
            importRow.setSelfFlagText(this.readCellAsString(row.getCell(col++)));
            importRow.setRestrictedFlagText(this.readCellAsString(row.getCell(col++)));
            importRow.setAntibioticFlagText(this.readCellAsString(row.getCell(col++)));
            importRow.setRxFlagText(this.readCellAsString(row.getCell(col++)));
            if (hasNewFlags) {
                importRow.setNationalMonitorFlagText(this.readCellAsString(row.getCell(col++)));
                importRow.setCentralPurchaseFlagText(this.readCellAsString(row.getCell(col)));
            }
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
        private List<MedicationCatalogImportRow> rows = new ArrayList<>();

        /** 原始上传字节（错误标注文件基底）。 */
        private byte[] originalBytes;

        public void setRows(List<MedicationCatalogImportRow> rows) {
            if (rows == null) {
                this.rows = new ArrayList<>();
                return;
            }
            this.rows = rows;
        }

        public void setOriginalBytes(byte[] originalBytes) {
            this.originalBytes = originalBytes;
        }
    }
}
