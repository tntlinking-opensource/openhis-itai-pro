/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.request;

import lombok.Data;

/**
 * 团检名册汇总请求。
 */
@Data
public class LoadRosterSummaryRequest {

    /** 批次主键。 */
    private Long batchId;
}
