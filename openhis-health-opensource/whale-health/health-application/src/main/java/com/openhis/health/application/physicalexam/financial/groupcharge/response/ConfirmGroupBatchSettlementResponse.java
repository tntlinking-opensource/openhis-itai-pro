/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.groupcharge.response;

import com.openhis.health.application.physicalexam.financial.groupcharge.dto.GroupChargeFailureRowDto;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 团检批量正式结算响应。
 */
@Getter
@Setter
public class ConfirmGroupBatchSettlementResponse {

    /** 成功人数。 */
    private int successCount;

    /** 失败明细。 */
    private List<GroupChargeFailureRowDto> failures = new ArrayList<>();
}
