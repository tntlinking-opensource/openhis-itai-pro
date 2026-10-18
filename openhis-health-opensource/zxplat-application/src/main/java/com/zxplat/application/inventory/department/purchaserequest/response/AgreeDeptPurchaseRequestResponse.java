/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.purchaserequest.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 审批同意科室采购申请成功响应（IF-DEP-CPR-08）。
 *
 * <p>与药库订货 {@code AgreePurchaseOrderResponse} 同构：仅返回成功标志。
 * 同意仅置 {@link com.zxplat.domain.share.enums.SupplyStatus#AGREE}，不创建订货、不改库存（BR-DEP-CPR-009）。</p>
 */
@Getter
@Setter
public class AgreeDeptPurchaseRequestResponse {

    /** 是否同意成功（领域 {@code agreeRequest} 完成后恒为 true）。 */
    private Boolean success;
}
