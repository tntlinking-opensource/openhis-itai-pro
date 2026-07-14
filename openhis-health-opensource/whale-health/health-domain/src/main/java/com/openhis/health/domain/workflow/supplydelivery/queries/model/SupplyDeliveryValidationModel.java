/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.supplydelivery.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 发放校验读模型：某供应请求行是否已存在完成态发放（用于编辑/撤销等门控）。
 *
 * <p>「完成」判定依赖读服务实现，通常与 {@code status_enum} 终态持久化 code 比对（B.10.8.1）。</p>
 */
@Getter
@Setter
public class SupplyDeliveryValidationModel {

    /** 供应请求行主键。 */
    private Long requestId;
    /** 是否已存在至少一条完成态发放。 */
    private Boolean hasCompletedDelivery;
}
