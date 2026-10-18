/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.diagnosiscatalog.support;

import com.zxplat.domain.clinical.conditiondefinition.command.ClinicalV2ImportRowCommand;
import com.zxplat.framework.core.application.exception.ValidationException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 解析国家临床版 2.0 官方 Excel（列：主要编码 / 附加编码 / 疾病名称）。
 */
@Component
public class ClinicalV2CatalogWorkbookParser {

    private final DataFormatter dataFormatter = new DataFormatter();

    /**
     * 解析工作簿为导入行。
     *
     * @param fileBytes xls/xlsx 字节
     * @return 有效行（编码、名称均非空）
     */
    public List<ClinicalV2ImportRowCommand> parse(byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new ValidationException("导入国家临床版2.0失败：文件不能为空");
        }
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(fileBytes))) {
            Sheet sheet = resolveSheet(workbook);
            if (sheet == null) {
                throw new ValidationException("导入国家临床版2.0失败：工作表为空");
            }
            int firstRow = sheet.getFirstRowNum();
            int lastRow = sheet.getLastRowNum();
            if (lastRow < firstRow) {
                throw new ValidationException("导入国家临床版2.0失败：无数据行");
            }
            Row header = sheet.getRow(firstRow);
            int codeCol = 0;
            int nameCol = 2;
            if (header != null) {
                Integer detectedCode = findHeaderColumn(header, "主要编码", "编码", "诊断编码", "疾病编码");
                Integer detectedName = findHeaderColumn(header, "疾病名称", "诊断名称", "名称");
                if (detectedCode != null) {
                    codeCol = detectedCode;
                }
                if (detectedName != null) {
                    nameCol = detectedName;
                }
            }
            List<ClinicalV2ImportRowCommand> rows = new ArrayList<>();
            for (int r = firstRow + 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                String code = trimToNull(cellText(row.getCell(codeCol)));
                String name = trimToNull(cellText(row.getCell(nameCol)));
                if (code == null || name == null) {
                    continue;
                }
                ClinicalV2ImportRowCommand cmd = new ClinicalV2ImportRowCommand();
                cmd.setConditionCode(code);
                cmd.setName(name);
                rows.add(cmd);
            }
            if (rows.isEmpty()) {
                throw new ValidationException("导入国家临床版2.0失败：未解析到有效数据行（需含主要编码与疾病名称）");
            }
            return rows;
        } catch (ValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ValidationException("导入国家临床版2.0失败：无法解析 Excel（" + ex.getMessage() + "）");
        }
    }

    private Sheet resolveSheet(Workbook workbook) {
        Sheet named = workbook.getSheet("2.0");
        if (named != null) {
            return named;
        }
        if (workbook.getNumberOfSheets() > 0) {
            return workbook.getSheetAt(0);
        }
        return null;
    }

    private Integer findHeaderColumn(Row header, String... candidates) {
        short last = header.getLastCellNum();
        for (int c = 0; c < last; c++) {
            String text = trimToNull(cellText(header.getCell(c)));
            if (text == null) {
                continue;
            }
            for (String candidate : candidates) {
                if (text.contains(candidate)) {
                    return c;
                }
            }
        }
        return null;
    }

    private String cellText(Cell cell) {
        if (cell == null) {
            return null;
        }
        return dataFormatter.formatCellValue(cell);
    }
}
