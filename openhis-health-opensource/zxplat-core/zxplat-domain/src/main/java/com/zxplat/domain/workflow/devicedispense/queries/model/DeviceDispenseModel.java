/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicedispense.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 耗材发放详情读模型（{@code loadDeviceDispenseById} / {@code mustGetDeviceDispense}）。
 */
@Getter
@Setter
public class DeviceDispenseModel {

    /** 主键。 */
    private Long id;

    /** 状态枚举编码。 */
    private String statusEnum;

    /** 数量。 */
    private BigDecimal quantity;

    /** 业务号。 */
    private String busNo;

    /** 就诊主键。 */
    private Long encounterId;

    /** 设备申请主键。 */
    private Long deviceReqId;

    /** 耗材定义主键。 */
    private Long deviceDefId;

    /** 发放库位主键。 */
    private Long locationId;

    /** 批号（拣配后）。 */
    private String lotNumber;

    /** 实际发放数量。 */
    private BigDecimal dispenseQuantity;

    /** 追溯码（逗号分隔，与实体 {@code trace_no} 对齐）。 */
    private String traceNo;

    /** 数量单位编码。 */
    private String unitCode;
}
