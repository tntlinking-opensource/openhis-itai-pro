/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.tracenumber.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * IF-TRC-RPT-02 列表行 DTO。
 */
@Getter
@Setter
public class TraceNumberReportRowDto {

    /** 追溯码。 */
    private String traceNo;

    /** 药品定义主键。 */
    private Long medicationId;

    /** 院内药品编码（enrich）。 */
    private String medicationCode;

    /** 药品名称（enrich）。 */
    private String medicationName;

    /** 规格文本（enrich，药品 totalVolume）。 */
    private String specificationText;

    /** 批号。 */
    private String lotNumber;

    /** 在院状态 code。 */
    private String inHospitalStatusEnum;

    /** 在院状态展示名。 */
    private String inHospitalStatusLabel;

    /** 拆零剩余量。 */
    private BigDecimal remainingSplitQuantity;

    /** 追溯码单位 code。 */
    private String traceUnitCode;

    /** 本库状态 code（兼容字段；指定库房仅返回在库码，对客列表不再展示）。 */
    private String locationStockStatusEnum;

    /** 本库状态展示名（兼容字段；对客列表不再展示）。 */
    private String locationStockStatusLabel;

    /** 本库净拆零量（LOCATION 范围）。 */
    private BigDecimal locationNetSplitQuantity;

    /** 当前所在库房 id（领域回填，应用层转名称）。 */
    private List<Long> currentLocationIds;

    /** 当前所在库房名称（多个以顿号拼接）。 */
    private String currentLocationNames;

    /** 最近流水时间。 */
    private LocalDateTime lastFlowAt;
}
