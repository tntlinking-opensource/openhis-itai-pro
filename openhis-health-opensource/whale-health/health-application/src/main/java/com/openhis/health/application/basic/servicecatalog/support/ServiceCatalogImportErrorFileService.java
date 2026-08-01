/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.servicecatalog.support;

import com.openhis.health.application.basic.servicecatalog.dto.ImportServiceCatalogErrorItemDto;
import com.openhis.health.domain.share.constants.ServiceCatalogImportConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 诊疗目录导入错误标注文件：基于用户原表追加「错误说明」列并短期 token 缓存。
 */
@Service
public class ServiceCatalogImportErrorFileService {

    private final ConcurrentHashMap<String, CachedErrorFile> cache = new ConcurrentHashMap<>();

    /**
     * 在原 workbook 副本上标注错误并缓存，返回下载 token。
     *
     * @param originalBytes 用户上传的原始 xlsx 字节
     * @param errors          全量错误项
     * @return UUID token
     */
    public String buildAndStore(byte[] originalBytes, List<ImportServiceCatalogErrorItemDto> errors) {
        // Gate：原文件非空
        if (originalBytes == null || originalBytes.length == 0) {
            throw new BusinessException(
                ServiceCatalogImportConstants.ERR_WRITE,
                "生成错误标注文件失败：原文件为空");
        }
        Map<Integer, List<String>> messagesByRow = this.groupMessagesByRow(errors);
        byte[] annotatedBytes = this.annotateWorkbook(originalBytes, messagesByRow);
        String token = UUID.randomUUID().toString();
        Instant expireAt = Instant.now().plusSeconds(
            (long) ServiceCatalogImportConstants.ERROR_FILE_TTL_MINUTES * 60L);
        cache.put(token, new CachedErrorFile(annotatedBytes, expireAt));
        return token;
    }

    /**
     * 按 token 取回错误标注文件字节。
     *
     * @param token 导入失败时返回的 token
     * @return xlsx 字节
     */
    public byte[] getBytes(String token) {
        // Gate：token 非空
        if (StringUtils.isEmpty(token)) {
            throw new BusinessException(
                ServiceCatalogImportConstants.ERR_TOKEN,
                "错误标注文件 token 无效或已过期");
        }
        CachedErrorFile cached = cache.get(token.trim());
        if (cached == null) {
            throw new BusinessException(
                ServiceCatalogImportConstants.ERR_TOKEN,
                "错误标注文件 token 无效或已过期");
        }
        if (Instant.now().isAfter(cached.expireAt())) {
            cache.remove(token.trim());
            throw new BusinessException(
                ServiceCatalogImportConstants.ERR_TOKEN,
                "错误标注文件 token 无效或已过期");
        }
        return cached.bytes();
    }

    /**
     * 按 Excel 行号聚合错误文案（中文分号拼接）。
     */
    private Map<Integer, List<String>> groupMessagesByRow(List<ImportServiceCatalogErrorItemDto> errors) {
        Map<Integer, List<String>> grouped = new HashMap<>();
        if (errors == null) {
            return grouped;
        }
        for (ImportServiceCatalogErrorItemDto error : errors) {
            if (error == null || error.getExcelRowNum() == null || StringUtils.isEmpty(error.getMessage())) {
                continue;
            }
            grouped.computeIfAbsent(error.getExcelRowNum(), key -> new ArrayList<>()).add(error.getMessage());
        }
        return grouped;
    }

    /**
     * 打开原表，在数据 Sheet 最右追加错误列并标红错误行。
     */
    private byte[] annotateWorkbook(byte[] originalBytes, Map<Integer, List<String>> messagesByRow) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(originalBytes);
             XSSFWorkbook workbook = new XSSFWorkbook(input);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet dataSheet = workbook.getSheet(ServiceCatalogImportConstants.SHEET_DATA);
            if (dataSheet == null) {
                throw new BusinessException(
                    ServiceCatalogImportConstants.ERR_WRITE,
                    "生成错误标注文件失败：缺少数据工作表");
            }
            int errorColIndex = ServiceCatalogImportConstants.COLUMN_HEADERS.length;
            Row headerRow = dataSheet.getRow(ServiceCatalogImportConstants.HEADER_ROW_INDEX);
            if (headerRow == null) {
                headerRow = dataSheet.createRow(ServiceCatalogImportConstants.HEADER_ROW_INDEX);
            }
            Cell headerCell = headerRow.createCell(errorColIndex);
            headerCell.setCellValue(ServiceCatalogImportConstants.ERROR_COLUMN_TITLE);
            CellStyle errorRowStyle = workbook.createCellStyle();
            errorRowStyle.setFillForegroundColor(IndexedColors.ROSE.getIndex());
            errorRowStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            for (Map.Entry<Integer, List<String>> entry : messagesByRow.entrySet()) {
                int excelRowNum = entry.getKey();
                int rowIndex = excelRowNum - 1;
                Row row = dataSheet.getRow(rowIndex);
                if (row == null) {
                    row = dataSheet.createRow(rowIndex);
                }
                String joined = String.join("；", entry.getValue());
                Cell errorCell = row.createCell(errorColIndex);
                errorCell.setCellValue(joined);
                for (int col = 0; col <= errorColIndex; col++) {
                    Cell cell = row.getCell(col);
                    if (cell == null) {
                        cell = row.createCell(col);
                    }
                    cell.setCellStyle(errorRowStyle);
                }
            }
            int dataSheetIndex = workbook.getSheetIndex(dataSheet);
            workbook.setActiveSheet(dataSheetIndex);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(
                ServiceCatalogImportConstants.ERR_WRITE,
                "生成错误标注文件失败：写出异常",
                ex);
        }
    }

    /** token 缓存条目。 */
    private record CachedErrorFile(byte[] bytes, Instant expireAt) {
    }
}
