/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicerequest.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 必取耗材请求查询模型。
 */
@Getter
@Setter
public class DeviceRequestQueryModel {
    /** 主键。 */
    private Long id;
    /** 状态枚举编码。 */
    private String statusEnum;
    /** 数量。 */
    private BigDecimal quantity;
    /** 退费设备主键。 */
    private Long refundDeviceId;
}
