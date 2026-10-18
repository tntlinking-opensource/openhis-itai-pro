/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.purchaserequest.request;

import com.zxplat.application.inventory.department.purchaserequest.dto.DeptPurchaseRequestLineDto;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 新建科室采购申请单请求（IF-DEP-CPR-04）。
 *
 * <p>领域批量取号生成 {@code busNo}；{@code typeEnum} 固定为
 * {@link com.zxplat.domain.share.enums.SupplyType#DEPT_PURCHASE_REQUEST}。
 * {@code purpose_type_enum} 由后端根据 {@code purposeLocationId} 推导。无供应商、无 {@code source_*}。</p>
 *
 * <p>应用入口：
 * {@link com.zxplat.application.inventory.department.purchaserequest.service.InventoryDepartmentPurchaseRequestAppService#createDeptPurchaseRequest(CreateDeptPurchaseRequestRequest)}。</p>
 */
@Getter
@Setter
public class CreateDeptPurchaseRequestRequest {

    /** 目的科室药房主键（{@code purpose_location_id}）：须在用户管辖科室药房门禁内；必填。 */
    private Long purposeLocationId;

    /** 制单时刻（{@code occurrence_time}）：头级统一写入各行；必填。 */
    private LocalDateTime occurrenceTime;

    /** 经手人医务人员主键（{@code practitioner_id}）：可选。 */
    private Long practitionerId;

    /**
     * 供应类别：{@link com.zxplat.domain.share.enums.SupplyCategory#getCode()}；可 blank，缺省库存供应。
     */
    private String categoryEnum;

    /** 单据头备注；行 {@code remake} 为空时回填至各行。 */
    private String remake;

    /** 明细行全量列表：非空。 */
    private List<DeptPurchaseRequestLineDto> lines;
}
