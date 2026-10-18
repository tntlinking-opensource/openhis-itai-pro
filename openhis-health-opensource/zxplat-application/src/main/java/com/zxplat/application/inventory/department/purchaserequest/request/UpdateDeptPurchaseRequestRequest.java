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
 * 修订科室采购申请单请求（IF-DEP-CPR-05）。
 *
 * <p>同号全量明细替换；{@link #rowVersion} 须与详情
 * {@link com.zxplat.application.inventory.department.purchaserequest.response.GetDeptPurchaseRequestDetailResponse#getRowVersion()}
 * 一致，冲突时 {@code DEP_CPR_105}。目的科室药房不可改异库。</p>
 *
 * <p>应用入口：
 * {@link com.zxplat.application.inventory.department.purchaserequest.service.InventoryDepartmentPurchaseRequestAppService#updateDeptPurchaseRequest(UpdateDeptPurchaseRequestRequest)}。</p>
 */
@Getter
@Setter
public class UpdateDeptPurchaseRequestRequest {

    /** 既有业务单号（{@code bus_no}）：必填。 */
    private String busNo;

    /** 目的科室药房主键：须与存量一致；必填。 */
    private Long purposeLocationId;

    /** 制单时刻：头级统一写入各行；必填。 */
    private LocalDateTime occurrenceTime;

    /** 经手人医务人员主键：可选。 */
    private Long practitionerId;

    /** 供应类别持久化码；可 blank，缺省库存供应。 */
    private String categoryEnum;

    /** 单据头备注；行 remake 为空时回填。 */
    private String remake;

    /** 明细全量替换列表：非空。 */
    private List<DeptPurchaseRequestLineDto> lines;

    /** 乐观锁版本：映射领域 {@code expectedRowVersionForRevision}。 */
    private Long rowVersion;
}
