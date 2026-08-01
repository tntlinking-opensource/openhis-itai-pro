/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicedispense.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库房期初期末明细耗材发退药明细行读模型（**AS-INV-RPT-001** · **DM-WKF-007 §6.2.19**）。
 *
 * <p>发/退方向由应用层按 {@code status_enum} 拆分动态列；本读模型不做品种汇总。</p>
 */
@Getter
@Setter
public class DeviceDispenseSettlementLineQueryModel implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 发放单主键。 */
    private Long id;

    /** 业务单号。 */
    private String busNo;

    /** 耗材定义主键（品种级汇总键）。 */
    private Long deviceDefId;

    /** 发放库位主键。 */
    private Long locationId;

    /** 发放数量（拆零最小单位）。 */
    private BigDecimal quantity;

    /** 单位编码。 */
    private String unitCode;

    /** 批号。 */
    private String lotNumber;

    /** 发放/退药完成时刻。 */
    private LocalDateTime dispenseTime;

    /** 发放状态持久化码。 */
    private String statusEnum;
}
