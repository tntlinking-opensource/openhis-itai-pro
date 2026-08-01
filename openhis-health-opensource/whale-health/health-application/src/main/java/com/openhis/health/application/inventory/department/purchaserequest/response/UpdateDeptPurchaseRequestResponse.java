/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.purchaserequest.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 修订科室采购申请单成功响应（IF-DEP-CPR-05）。
 *
 * <p>同号全量替换明细后返回业务单号；状态回到待审核以便重新提交审批。</p>
 */
@Getter
@Setter
public class UpdateDeptPurchaseRequestResponse {

    /** 同号修订后的业务单号（与请求 {@code busNo} 一致）。 */
    private String busNo;

    /**
     * 修订后状态持久化码：固定为
     * {@link com.openhis.health.domain.share.enums.SupplyStatus#PENDING_APPROVAL}（修订回待审核）。
     */
    private String statusEnum;
}
