/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicedispense.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 医嘱开立目录用：同就诊下耗材发放预占行（候选库位 ∩ 目录耗材定义）。
 *
 * <p>供 {@code DeviceDispenseQueryService#listDeviceDispensesForOrderCatalogOccupancy} 返回；状态列为字符串码，与耗材发放聚合持久化一致；候选库房全局，不限就诊。</p>
 */
@Getter
@Setter
public class DeviceDispenseOrderCatalogOccupancyModel {

    /** 耗材定义主键（与目录行耗材 id 对齐）。 */
    private Long deviceDefId;

    /** 发放占用的库房库位。 */
    private Long locationId;

    /** 发放行声明数量（业务单位；目录预减须换算为拆零单位）。 */
    private BigDecimal quantity;

    /** 发放行数量单位编码。 */
    private String unitCode;

    /** 发放状态码（与 {@link com.openhis.health.domain.share.enums.DispenseStatus#getCode()} 一致）。 */
    private String statusEnum;
}
