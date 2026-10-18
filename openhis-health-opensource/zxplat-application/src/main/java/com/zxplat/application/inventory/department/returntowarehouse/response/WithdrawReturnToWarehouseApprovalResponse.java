/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.returntowarehouse.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 撤回科室退库审批成功响应。
 */
@Getter
@Setter
public class WithdrawReturnToWarehouseApprovalResponse {

    /** 操作是否成功。 */
    private Boolean success;
    /** 单据状态持久化码。 */
    private Integer statusEnum;
}
