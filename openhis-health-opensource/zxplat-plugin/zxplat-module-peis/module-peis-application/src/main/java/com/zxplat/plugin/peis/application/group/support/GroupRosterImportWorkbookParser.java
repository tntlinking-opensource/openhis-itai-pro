/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.support;

import com.zxplat.plugin.peis.application.group.dto.RosterImportRowDto;
import com.zxplat.domain.share.enums.AdministrativeGender;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.framework.core.application.util.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 团检名册导入 xlsx 解析器。
 */
@Component
public class GroupRosterImportWorkbookParser {

    private final DataFormatter dataFormatter = new DataFormatter();

    /**
     * 解析模板文件为名册导入行。
     *
     * @param bytes      xlsx 字节
     * @param memberKind 期望人员类型
     * @return 解析结果
     */
    public List<RosterImportRowDto> parse(byte[] bytes, GroupRosterImportMemberKind memberKind) {
        if (memberKind == null) {
            throw new ValidationException("模板导入失败：人员类型不能为空");
        }
        if (bytes == null || bytes.length == 0) {
            throw new ValidationException("模板导入失败：文件不能为空");
        }
        if (bytes.length > GroupRosterImportConstants.MAX_FILE_BYTES) {
            throw new ValidationException("模板导入失败：文件过大，请控制在 5MB 以内");
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes);
             XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            Sheet dataSheet = workbook.getSheet(GroupRosterImportConstants.SHEET_DATA);
            Sheet sysSheet = workbook.getSheet(GroupRosterImportConstants.SHEET_SYS);
            if (dataSheet == null) {
                throw new BusinessException("ROSTER_TEMPLATE_INVALID", "模板导入失败：缺少「名册」工作表，请重新下载模板");
            }
            assertSysMetadata(sysSheet, memberKind);
            assertHeaders(dataSheet, memberKind);
            return readDataRows(dataSheet, memberKind);
        } catch (IOException ex) {
            throw new BusinessException("ROSTER_TEMPLATE_OPEN_FAILED", "模板导入失败：无法打开 xlsx 文件", ex);
        }
    }

    private void assertSysMetadata(Sheet sysSheet, GroupRosterImportMemberKind expectedKind) {
        if (sysSheet == null) {
            throw new BusinessException("ROSTER_TEMPLATE_INVALID", "模板导入失败：模板版本不匹配，请重新下载模板");
        }
        String kind = readSysValue(sysSheet, GroupRosterImportConstants.SYS_KIND);
        String version = readSysValue(sysSheet, GroupRosterImportConstants.SYS_VERSION);
        GroupRosterImportMemberKind fileKind = GroupRosterImportMemberKind.parse(kind);
        if (fileKind == null || fileKind != expectedKind) {
            throw new BusinessException(
                "ROSTER_TEMPLATE_KIND_MISMATCH",
                "模板导入失败：模板人员类型与当前选择不一致，请下载正确的模板");
        }
        if (!GroupRosterImportConstants.TEMPLATE_VERSION.equals(version)) {
            throw new BusinessException("ROSTER_TEMPLATE_VERSION_MISMATCH", "模板导入失败：模板版本过旧，请重新下载");
        }
    }

    private String readSysValue(Sheet sysSheet, String key) {
        for (int rowIndex = 0; rowIndex <= sysSheet.getLastRowNum(); rowIndex++) {
            Row row = sysSheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            String cellKey = cellString(row.getCell(0));
            if (key.equals(cellKey)) {
                return cellString(row.getCell(1));
            }
        }
        return null;
    }

    private void assertHeaders(Sheet dataSheet, GroupRosterImportMemberKind memberKind) {
        Row headerRow = dataSheet.getRow(0);
        if (headerRow == null) {
            throw new BusinessException("ROSTER_TEMPLATE_HEADER_INVALID", "模板导入失败：表头不能为空");
        }
        String[] expected = memberKind == GroupRosterImportMemberKind.STUDENT
            ? GroupRosterImportConstants.STUDENT_HEADERS
            : GroupRosterImportConstants.STAFF_HEADERS;
        int legacyCount = GroupRosterImportConstants.LEGACY_HEADER_COUNT;
        for (int col = 0; col < legacyCount; col++) {
            String actual = normalizeHeader(cellString(headerRow.getCell(col)));
            String want = normalizeHeader(expected[col]);
            if (!want.equals(actual)) {
                throw new BusinessException(
                    "ROSTER_TEMPLATE_HEADER_INVALID",
                    "模板导入失败：第 " + (col + 1) + " 列表头应为「" + expected[col] + "」");
            }
        }
    }

    private List<RosterImportRowDto> readDataRows(Sheet dataSheet, GroupRosterImportMemberKind memberKind) {
        List<RosterImportRowDto> rows = new ArrayList<>();
        int lastRow = dataSheet.getLastRowNum();
        for (int rowIndex = 1; rowIndex <= lastRow; rowIndex++) {
            Row row = dataSheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            String fullName = cellString(row.getCell(0));
            if (StringUtils.isEmpty(fullName)) {
                continue;
            }
            if (fullName.contains("示例")) {
                continue;
            }
            RosterImportRowDto dto = new RosterImportRowDto();
            dto.setFullName(fullName);
            dto.setGenderEnum(resolveGender(cellString(row.getCell(1)), rowIndex + 1));
            dto.setIdCardNo(cellString(row.getCell(2)));
            dto.setMobilePhone(cellString(row.getCell(3)));
            if (memberKind == GroupRosterImportMemberKind.STUDENT) {
                dto.setStudentNo(cellString(row.getCell(4)));
                dto.setClassName(cellString(row.getCell(5)));
            } else {
                dto.setEmployeeNo(cellString(row.getCell(4)));
                dto.setClassName(cellString(row.getCell(5)));
            }
            dto.setBatchNameOverride(cellString(row.getCell(6)));
            dto.setPackageNames(cellString(row.getCell(7)));
            assertRowIdentity(dto, memberKind, rowIndex + 1);
            rows.add(dto);
            if (rows.size() > GroupRosterImportConstants.MAX_DATA_ROWS) {
                throw new ValidationException(
                    "模板导入失败：单次最多导入 " + GroupRosterImportConstants.MAX_DATA_ROWS + " 行");
            }
        }
        if (rows.isEmpty()) {
            throw new ValidationException("模板导入失败：未解析到有效数据行");
        }
        return rows;
    }

    private void assertRowIdentity(RosterImportRowDto row, GroupRosterImportMemberKind memberKind, int excelRowNum) {
        if (trimToNull(row.getIdCardNo()) != null) {
            return;
        }
        if (trimToNull(row.getMobilePhone()) != null) {
            return;
        }
        if (memberKind == GroupRosterImportMemberKind.STUDENT && trimToNull(row.getStudentNo()) != null) {
            return;
        }
        if (memberKind.usesEmployeeNo() && trimToNull(row.getEmployeeNo()) != null) {
            return;
        }
        String label = memberKind == GroupRosterImportMemberKind.STUDENT ? "学号" : "工号";
        throw new ValidationException(
            "模板导入失败：第 " + excelRowNum + " 行请至少填写身份证、手机号或" + label);
    }

    private String resolveGender(String raw, int excelRowNum) {
        if (StringUtils.isEmpty(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        if ("男".equals(trimmed)) {
            return AdministrativeGender.MALE.getCode();
        }
        if ("女".equals(trimmed)) {
            return AdministrativeGender.FEMALE.getCode();
        }
        if ("未知".equals(trimmed)) {
            return AdministrativeGender.UNKNOWN.getCode();
        }
        AdministrativeGender gender = EnumUtil.fromCode(AdministrativeGender.class, trimmed.toUpperCase());
        if (gender == null) {
            throw new ValidationException("模板导入失败：第 " + excelRowNum + " 行性别无效");
        }
        return gender.getCode();
    }

    private static String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }
        return header.trim().replace("*", "");
    }

    private String cellString(Cell cell) {
        if (cell == null) {
            return null;
        }
        String value = dataFormatter.formatCellValue(cell);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }
}
