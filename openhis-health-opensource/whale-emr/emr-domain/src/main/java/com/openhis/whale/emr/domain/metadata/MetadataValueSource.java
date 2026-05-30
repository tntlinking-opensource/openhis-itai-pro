/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.whale.emr.domain.metadata;

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
