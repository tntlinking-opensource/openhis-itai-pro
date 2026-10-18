/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.supplydelivery.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 汇总单头发药态读模型（按 {@code request_id} 关联 SupplyRequest 行）。
 */
@Getter
@Setter
public class SupplyDeliverySlipHeaderQueryModel {

    /** 上游供应请求行主键。 */
    private Long requestId;

    /** 领药人主键。 */
    private Long receiverId;

    /** 发放状态持久化码。 */
    private String statusEnum;

    /** 发药时间（{@code receive_time}）。 */
    private LocalDateTime receiveTime;
}
