/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicedispense.queries;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 库房日结耗材发退药批量读入参（**AS-INV-RPT-001** · **DM-WKF-007 §6.2.19**）。
 *
 * <p>时间轴为 {@code dispense_time}；自然日边界由应用层换算后写入起止时刻。</p>
 */
@Getter
@Setter
public class DeviceDispenseSettlementWindowQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 发放库位主键（{@code location_id}）。 */
    private Long locationId;

    /** 发放时刻下界（含）。 */
    private LocalDateTime dispenseWindowStartInclusive;

    /** 发放时刻上界（含）。 */
    private LocalDateTime dispenseWindowEndInclusive;

    /**
     * 状态白名单（{@link com.openhis.health.domain.share.enums.DispenseStatus#getCode()}）；
     * {@code null} 或空时读侧默认仅 {@code COMPLETED} 与 {@code REFUNDED}。
     */
    private List<String> statusEnumWhitelist;
}
