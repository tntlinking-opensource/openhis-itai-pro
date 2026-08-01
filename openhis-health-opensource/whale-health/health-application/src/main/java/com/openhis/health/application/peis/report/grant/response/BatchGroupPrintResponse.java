/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.report.grant.response;

import lombok.Data;

import java.util.List;

/**
 * 团体批量打印响应（IF-PEIS-REP-06）。
 */
@Data
public class BatchGroupPrintResponse {

    /** 成功份数。 */
    private Integer successCount;

    /** 失败就诊主键列表。 */
    private List<Long> failedEncounterIds;

    /** 提示信息。 */
    private String message;
}
