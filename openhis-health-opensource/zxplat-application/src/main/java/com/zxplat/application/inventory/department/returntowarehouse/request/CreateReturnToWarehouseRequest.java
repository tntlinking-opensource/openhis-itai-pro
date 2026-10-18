/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.returntowarehouse.request;

import com.zxplat.application.inventory.department.returntowarehouse.dto.ReturnToWarehouseLineDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 新建科室退库单请求（IF-DEP-RTW-04）。
 *
 * <p>{@code originalBusNo} 可选：有值时明细须绑定该已同意入库单可退余量；无值时允许按源科室药房库存自由选品（对偶药房退库 IF-PHA-RTW）。</p>
 */
@Getter
@Setter
public class CreateReturnToWarehouseRequest {

    /**
     * 来源/关联科室入库单业务单号；可选。
     *
     * <p>为空表示自由选品退库；非空时走入库单可退余量校验（须已同意 {@code DEPT_STOCK_IN}）。</p>
     */
    private String originalBusNo;

    /** 源科室药房库位主键（{@code LocationForm.DEPARTMENT_PHARMACY}）。 */
    private Long sourceLocationId;

    /** 目的药库库位主键（{@code LocationForm.WAREHOUSE}）。 */
    private Long purposeLocationId;

    /** 源位置类型 API sort（应用层按库位形态解析持久化 code）。 */
    private Integer sourceTypeEnum;

    /** 目的位置类型 API sort（应用层按库位形态解析持久化 code）。 */
    private Integer purposeTypeEnum;

    /** 供应类别 API sort（药品/耗材等；空则由明细或默认解析）。 */
    private Integer categoryEnum;

    /** 供应商主键；可选，库存五元组匹配用。 */
    private Long supplierId;

    /** 经手人医务人员主键；与制单人分离，来自前端下拉，建单必填。 */
    private Long practitionerId;

    /** 退库明细行；至少一行，数量须为正。 */
    private List<ReturnToWarehouseLineDto> lines;
}
