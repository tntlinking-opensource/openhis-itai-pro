/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.domain.metadata;

/**
 * 元数据字段取值来源策略（与《元数据字段定义与维护》§3.3 对齐）。
 */
public enum MetadataValueSource {

    PATIENT,
    ENCOUNTER,
    ADMISSION,
    INSURANCE,
    ORG,
    DEPT,
    WARD,
    PRACTITIONER,
    USER,
    USER_DEPT,
    DOCUMENT,
    TEMPLATE,
    VERSION,
    DIAGNOSIS,
    ALLERGY,
    VITAL_SIGN,
    LAB_SUMMARY,
    EXAM_SUMMARY,
    DICT,
    READ_MODEL,
    SETTING,
    SYSTEM,
    CONST,
    EXPR,
    FIELD_REF,
    HTTP_API,
    REQUEST,
    MANUAL
}
