/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.purchaserequest.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 科室采购申请单详情请求（IF-DEP-CPR-03）。
 *
 * <p>应用入口：
 * {@link com.openhis.health.application.inventory.department.purchaserequest.service.InventoryDepartmentPurchaseRequestAppService#getDeptPurchaseRequestDetail(GetDeptPurchaseRequestDetailRequest)}。</p>
 */
@Getter
@Setter
public class GetDeptPurchaseRequestDetailRequest {

    /** 业务单号；应用服务门控非空并对首尾空白 {@code trim}。 */
    private String busNo;
}
