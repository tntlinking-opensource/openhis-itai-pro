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
 * 新建药房退库单请求（须关联已同意药房入库单）。
 */
@Getter
@Setter
public class CreateReturnToWarehouseRequest {

    /** 来源/关联业务单号。 */
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
