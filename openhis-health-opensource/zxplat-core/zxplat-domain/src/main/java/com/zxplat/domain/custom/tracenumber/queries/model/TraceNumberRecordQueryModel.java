/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.tracenumber.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 追溯码记录读模型（DM-SDD §4.6.2）。
 */
@Getter
@Setter
public class TraceNumberRecordQueryModel {

    /** 记录标识。 */
    private Long id;

    /** 追溯码。 */
    private String traceNo;

    /** 记录类别。 */
    private String recordCategoryEnum;

    /** 轨迹方向。 */
    private String recordDirectionEnum;

    /** 库位标识。 */
    private Long locationId;

    /** 业务数量。 */
    private BigDecimal quantity;

    /** 业务单位。 */
    private String unitCode;

    /** 拆零量。 */
    private BigDecimal splitQuantity;

    /** 操作类型。 */
    private String operationType;

    /** 业务单号。 */
    private String busNo;

    /** 批号。 */
    private String lotNumber;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
