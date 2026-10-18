/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicedispense.queries.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 耗材使用报表患者侧明细行（单条 DeviceDispense）。
 */
@Getter
@Setter
public class ConsumableUsageDispenseLineQueryModel {

    /** 发放单主键。 */
    private Long dispenseId;

    /**
     * 源发放主键（退药行 {@code based_on_id}；正向行为空）。
     *
     * <p>账单仍锚在源 COMPLETED 发放上；报表售价须经此字段回挂 ChargeItem。</p>
     */
    private Long basedOnDispenseId;

    /** 耗材申请主键。 */
    private Long deviceReqId;

    /** 耗材定义主键。 */
    private Long deviceDefId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 发放库位主键。 */
    private Long locationId;

    /** 状态码。 */
    private String statusEnum;

    /** 单位码。 */
    private String unitCode;

    /** 数量（落库正数；汇总时退药取负）。 */
    private BigDecimal quantity;

    /** 发放业务时间。 */
    private LocalDateTime dispenseTime;

    /** 批号（可空）。 */
    private String lotNumber;

    /** 执行人主键（可空）。 */
    private Long performerId;
}
