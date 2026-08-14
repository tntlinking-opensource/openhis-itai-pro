/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.returntowarehouse.request;

import com.openhis.health.application.inventory.pharmacy.returntowarehouse.dto.ReturnToWarehouseLineDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 新建药房退库单请求。
 *
 * <p>{@code originalBusNo} 可选：有值时明细须绑定该已同意入库单可退余量；无值时允许按源药房库存自由选品。</p>
 */
@Getter
@Setter
public class CreateReturnToWarehouseRequest {

    /**
     * 来源/关联药房入库单业务单号；可选。
     *
     * <p>为空表示自由选品退库；非空时走入库单可退余量校验。</p>
     */
    private String originalBusNo;
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
    /** 明细行列表。 */
    private List<ReturnToWarehouseLineDto> lines;
}
