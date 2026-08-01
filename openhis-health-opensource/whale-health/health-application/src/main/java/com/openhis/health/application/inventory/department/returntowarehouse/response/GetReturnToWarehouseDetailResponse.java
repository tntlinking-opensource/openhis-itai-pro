/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.returntowarehouse.response;

import com.openhis.health.application.inventory.department.returntowarehouse.dto.ReturnToWarehouseDetailLineDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 科室退库单详情响应（头 + 明细行）。
 */
@Getter
@Setter
public class GetReturnToWarehouseDetailResponse {

    /** 业务单号。 */
    private String busNo;
    /** 来源/关联业务单号。 */
    private String originalBusNo;
    /** 单据类型持久化码。 */
    private Integer typeEnum;
    /** 单据状态持久化码。 */
    private Integer statusEnum;
    /** 供应商主键。 */
    private Long supplierId;
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
    /** 经手人主键。 */
    private Long practitionerId;
    /** 申请人（制单人）主键。 */
    private Long applicantId;
    /** 明细行列表。 */
    private List<ReturnToWarehouseDetailLineDto> lines;
}
