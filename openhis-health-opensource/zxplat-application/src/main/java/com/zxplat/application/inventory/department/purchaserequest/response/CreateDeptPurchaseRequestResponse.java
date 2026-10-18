/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.purchaserequest.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 新建科室采购申请单成功响应（IF-DEP-CPR-04）。
 *
 * <p>返回领域取号后的 {@code busNo} 与初始草稿状态码（待审核）。</p>
 */
@Getter
@Setter
public class CreateDeptPurchaseRequestResponse {

    /** 新建申请单业务单号（领域按 {@code DPR} 序号键取号）。 */
    private String busNo;

    /**
     * 新建后初始状态持久化码：固定为
     * {@link com.zxplat.domain.share.enums.SupplyStatus#PENDING_APPROVAL}。
     */
    private String statusEnum;
}
