/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.request;

import lombok.Data;

/**
 * 团检名册汇总请求。
 */
@Data
public class LoadRosterSummaryRequest {

    /** 批次主键。 */
    private Long batchId;
}
