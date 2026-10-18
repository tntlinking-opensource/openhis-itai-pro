/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.procedurecatalog.support;

import com.zxplat.domain.clinical.proceduredefinition.command.ClinicalProcV3ImportRowCommand;
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
 * 解析国家临床版手术 3.0 官方 Excel（列：主要编码 / 附加编码 / 手术操作名称 / 类别）。
 * <p>官方包 sheet「3.0」物理行数可能远大于有效数据行；连续空行达到阈值后提前结束扫描。
 */
@Component
public class ClinicalProcV3CatalogWorkbookParser {

    private static final int EMPTY_STREAK_STOP = 50;

    private final DataFormatter dataFormatter = new DataFormatter();

    /**
     * 解析工作簿为导入行。
     *
     * @param fileBytes xls/xlsx 字节
     * @return 有效行（编码、名称均非空）
     */
    public List<ClinicalProcV3ImportRowCommand> parse(byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new ValidationException("导入国家临床版手术3.0失败：文件不能为空");
        }
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(fileBytes))) {
            Sheet sheet = resolveSheet(workbook);
            if (sheet == null) {
                throw new ValidationException("导入国家临床版手术3.0失败：工作表为空");
            }
            int firstRow = sheet.getFirstRowNum();
            int lastRow = sheet.getLastRowNum();
            if (lastRow < firstRow) {
                throw new ValidationException("导入国家临床版手术3.0失败：无数据行");
            }
            Row header = sheet.getRow(firstRow);
            int mainCodeCol = 0;
            int addCodeCol = 1;
            int nameCol = 2;
            int typeCol = 3;
            if (header != null) {
                Integer detectedMain = findHeaderColumn(header, "主要编码");
                Integer detectedAdd = findHeaderColumn(header, "附加编码");
                Integer detectedCode = findHeaderColumn(header, "手术编码", "手术操作编码", "编码");
                Integer detectedName = findHeaderColumn(header, "手术操作名称", "名称", "手术名称");
                Integer detectedType = findHeaderColumn(header, "类别", "类型");
                if (detectedMain != null) {
                    mainCodeCol = detectedMain;
                } else if (detectedCode != null) {
                    mainCodeCol = detectedCode;
                }
                if (detectedAdd != null) {
                    addCodeCol = detectedAdd;
                }
                if (detectedName != null) {
                    nameCol = detectedName;
                }
                if (detectedType != null) {
                    typeCol = detectedType;
                }
            }
            List<ClinicalProcV3ImportRowCommand> rows = new ArrayList<>();
            int emptyStreak = 0;
            for (int r = firstRow + 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    emptyStreak++;
                    if (emptyStreak >= EMPTY_STREAK_STOP) {
                        break;
                    }
                    continue;
                }
                String mainCode = trimToNull(cellText(row.getCell(mainCodeCol)));
                String addCode = trimToNull(cellText(row.getCell(addCodeCol)));
                // 主码优先；主码空时回落附加码
                String code;
                if (mainCode != null) {
                    code = mainCode;
                } else {
                    code = addCode;
                }
                String name = trimToNull(cellText(row.getCell(nameCol)));
                if (code == null && name == null) {
                    emptyStreak++;
                    if (emptyStreak >= EMPTY_STREAK_STOP) {
                        break;
                    }
                    continue;
                }
                emptyStreak = 0;
                if (code == null || name == null) {
                    continue;
                }
                ClinicalProcV3ImportRowCommand cmd = new ClinicalProcV3ImportRowCommand();
                cmd.setProcedureCode(code);
                cmd.setName(name);
                cmd.setTypeCode(trimToNull(cellText(row.getCell(typeCol))));
                rows.add(cmd);
            }
            if (rows.isEmpty()) {
                throw new ValidationException("导入国家临床版手术3.0失败：未解析到有效数据行（需含编码与手术操作名称）");
            }
            return rows;
        } catch (ValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ValidationException("导入国家临床版手术3.0失败：无法解析 Excel（" + ex.getMessage() + "）");
        }
    }

    private Sheet resolveSheet(Workbook workbook) {
        Sheet named = workbook.getSheet("3.0");
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
