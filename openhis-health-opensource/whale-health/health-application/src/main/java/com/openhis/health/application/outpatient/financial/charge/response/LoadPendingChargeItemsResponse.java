/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.response;

import com.openhis.health.application.outpatient.financial.charge.dto.PendingChargeItemRowDto;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 加载待收费明细响应。
 */
@Data
@Accessors(chain = true)
public class LoadPendingChargeItemsResponse {
    /**
     * 就诊主键。
     */
    private Long encounterId;
    /**
     * 待收费项目明细列表。
     */
    private List<PendingChargeItemRowDto> items;
    /**
     * 是否允许就诊维度转医保/转自费（须同时存在默认自费与医保账户，纯自费挂号为 false）。
     */
    private Boolean accountSwitchEnabled;
}
