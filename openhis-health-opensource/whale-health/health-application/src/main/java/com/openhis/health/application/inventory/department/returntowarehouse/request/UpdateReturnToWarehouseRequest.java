/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.returntowarehouse.request;

import com.openhis.health.application.inventory.department.returntowarehouse.dto.ReturnToWarehouseLineDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 修订科室退库单请求（软删重插全量对齐明细）。
 */
@Getter
@Setter
public class UpdateReturnToWarehouseRequest {

    /** 业务单号。 */
    private String busNo;
    /** 源库位主键。 */
    private Long sourceLocationId;
    /** 目的库位主键。 */
    private Long purposeLocationId;
    /** 源位置类型持久化码。 */
    private Integer sourceTypeEnum;
    /** 目的位置类型持久化码。 */
    private Integer purposeTypeEnum;
    /** 供应类别持久化码。 */
    private Integer categoryEnum;
    /** 供应商主键。 */
    private Long supplierId;
    /** 经手人主键。 */
    private Long practitionerId;
    /** 明细行列表。 */
    private List<ReturnToWarehouseLineDto> lines;
    /** 禁止传入；传入时应用层返回 DEPT_RTW_105。 */
    private Integer rowVersion;
}
