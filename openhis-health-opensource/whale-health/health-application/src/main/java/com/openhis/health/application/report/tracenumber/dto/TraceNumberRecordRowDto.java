/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.tracenumber.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * IF-TRC-RPT-04 流转明细行 DTO。
 */
@Getter
@Setter
public class TraceNumberRecordRowDto {

    /** 记录主键。 */
    private Long id;

    /** 记录类别 code。 */
    private String recordCategoryEnum;

    /** 记录类别展示名。 */
    private String recordCategoryLabel;

    /** 记录方向 code。 */
    private String recordDirectionEnum;

    /** 记录方向展示名。 */
    private String recordDirectionLabel;

    /** 库位主键。 */
    private Long locationId;

    /** 库位名称（enrich）。 */
    private String locationName;

    /** 业务数量。 */
    private BigDecimal quantity;

    /** 单位 code。 */
    private String unitCode;

    /** 单位展示名。 */
    private String unitCodeLabel;

    /** 操作类型 code。 */
    private String operationType;

    /** 操作类型展示名。 */
    private String operationTypeLabel;

    /** 业务单号。 */
    private String busNo;

    /** 记录创建时间。 */
    private LocalDateTime createdAt;

    /** 患者姓名（发药/退药 enrich；次要列）。 */
    private String patientName;
}
