/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.supplydelivery.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 供应发放摘要读模型（列表或门禁场景最小字段集）。
 *
 * <p>{@code statusEnum} 为 {@link com.zxplat.domain.share.enums.DispenseStatus} 等持久化 {@code getCode()}（B.10.8.1）。</p>
 */
@Getter
@Setter
public class SupplyDeliveryModel {

    /** 发放主键。 */
    private Long id;
    /** 发放状态持久化码。 */
    private String statusEnum;
    /** 上游供应请求行主键。 */
    private Long requestId;
}
