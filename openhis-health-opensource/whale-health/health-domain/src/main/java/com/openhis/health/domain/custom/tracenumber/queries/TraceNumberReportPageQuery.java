/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.tracenumber.queries;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 追溯码报表分页入参（DM-CUS-010 §6.2.8 · AS-CUS-TRC-002）。
 */
@Getter
@Setter
public class TraceNumberReportPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 查询范围：HOSPITAL / LOCATION。 */
    private String queryScope;

    /** 指定库位（queryScope=LOCATION 时必填）。 */
    private Long locationId;

    /** 全院在院状态筛选。 */
    private String inHospitalStatusEnum;

    /** 本库状态筛选（仅 LOCATION 范围有效）。 */
    private String locationStockStatusEnum;

    /** 追溯码关键字。 */
    private String traceNoKeyword;

    /** 应用层预解析的药品定义主键列表。 */
    private List<Long> medicationItemIds;

    /** 批号关键字。 */
    private String lotNumberKeyword;

    /** 流水时间下限。 */
    private LocalDateTime flowTimeStart;

    /** 流水时间上限。 */
    private LocalDateTime flowTimeEnd;
}
