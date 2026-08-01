/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.report.grant.request;

import lombok.Data;

import java.util.List;

/**
 * 团体报告批量打印请求（IF-PEIS-REP-06）。
 */
@Data
public class BatchGroupPrintRequest {

    /** 团检批次主键。 */
    private Long groupBatchId;

    /** 就诊主键列表（空则依赖后续批次展开，当前桩按传入列表处理）。 */
    private List<Long> encounterIds;

    /** 操作人标识。 */
    private Long operatorId;
}
