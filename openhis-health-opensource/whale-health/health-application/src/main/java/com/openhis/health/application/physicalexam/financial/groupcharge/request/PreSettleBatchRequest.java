/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.groupcharge.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 团检批量预结算请求。
 */
@Getter
@Setter
public class PreSettleBatchRequest {

    /** 名册主键列表。 */
    private List<Long> rosterIds;
}
