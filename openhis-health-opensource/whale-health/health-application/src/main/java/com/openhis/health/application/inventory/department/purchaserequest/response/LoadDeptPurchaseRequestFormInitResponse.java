/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.purchaserequest.response;

import com.openhis.whale.core.application.dto.OptionDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 科室采购申请页初始化响应（IF-DEP-CPR-01）。
 *
 * <p>{@code departmentPharmacyLocationOptions} 首项约定 {@code value="0"}、{@code label="全部"}，
 * 供列表 {@link com.openhis.health.application.inventory.department.purchaserequest.request.PageDeptPurchaseRequestsRequest#getPurposeLocationId()} 筛选。</p>
 */
@Getter
@Setter
public class LoadDeptPurchaseRequestFormInitResponse {

    /** 科室药房库位下拉：首项为「全部」（{@code value="0"}），其余为管辖内启用科室药房。 */
    private List<OptionDto> departmentPharmacyLocationOptions;

    /** 单据状态下拉：{@code value} 为 {@link com.openhis.health.domain.share.enums.SupplyStatus#getCode()}。 */
    private List<OptionDto> statusOptions;

    /** 物品来源表下拉：药品/耗材，与选品白名单一致。 */
    private List<OptionDto> purchaseItemTableTypeOptions;

    /** 药师下拉（经手人候选，平台角色 ROLE_PHARMACIST）。 */
    private List<OptionDto> pharmacistOptions;

    /** 库管下拉（经手人候选，平台角色 ROLE_WAREHOUSE）。 */
    private List<OptionDto> warehouseKeeperOptions;
}
