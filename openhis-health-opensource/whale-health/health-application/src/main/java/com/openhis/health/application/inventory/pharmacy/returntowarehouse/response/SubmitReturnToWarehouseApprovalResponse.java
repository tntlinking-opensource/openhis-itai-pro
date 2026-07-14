/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.returntowarehouse.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 提交药房退库审批成功响应。
 */
@Getter
@Setter
public class SubmitReturnToWarehouseApprovalResponse {

    /** 操作是否成功。 */
    private Boolean success;
    /** 单据状态持久化码。 */
    private Integer statusEnum;
}
