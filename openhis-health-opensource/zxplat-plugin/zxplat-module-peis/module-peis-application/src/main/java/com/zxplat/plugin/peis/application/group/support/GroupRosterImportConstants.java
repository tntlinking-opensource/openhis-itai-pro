/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.support;

/**
 * 团检名册 Excel 模板常量。
 */
public final class GroupRosterImportConstants {

    private GroupRosterImportConstants() {
    }

    public static final String TEMPLATE_VERSION = "v1.0";
    public static final String XLSX_CONTENT_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final int MAX_DATA_ROWS = 5000;
    public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;

    public static final String SHEET_GUIDE = "填写说明";
    public static final String SHEET_DATA = "名册";
    public static final String SHEET_SYS = "_sys";

    public static final String SYS_KIND = "memberKind";
    public static final String SYS_VERSION = "templateVersion";

    public static final String[] STUDENT_HEADERS = {"姓名*", "性别", "身份证号", "手机号", "学号", "班级", "分组", "套餐"};
    public static final String[] STAFF_HEADERS = {"姓名*", "性别", "身份证号", "手机号", "工号", "部门", "分组", "套餐"};
    public static final String[] ENTERPRISE_HEADERS = STAFF_HEADERS;

    /** v1.0 模板最少列数（不含分组/套餐）。 */
    public static final int LEGACY_HEADER_COUNT = 6;

    public static final String STUDENT_TEMPLATE_FILE =
        "团检名册导入模板_学生_" + TEMPLATE_VERSION + ".xlsx";
    public static final String STAFF_TEMPLATE_FILE =
        "团检名册导入模板_教职工_" + TEMPLATE_VERSION + ".xlsx";
    public static final String ENTERPRISE_TEMPLATE_FILE =
        "团检名册导入模板_企事业_" + TEMPLATE_VERSION + ".xlsx";

    public static final String[] STUDENT_EXAMPLE =
        {"张三（示例）", "男", "", "13800138000", "20240001", "一年级1班"};
    public static final String[] STAFF_EXAMPLE =
        {"李四（示例）", "女", "", "13900139000", "T10001", "技术部"};
    public static final String[] ENTERPRISE_EXAMPLE =
        {"王五（示例）", "男", "", "13700137000", "E10001", "综合部"};
}
