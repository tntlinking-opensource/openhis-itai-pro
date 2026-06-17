/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.department.deptreturn.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmitDeptReturnApprovalResponse {

    /** 操作是否成功。 */
    private Boolean success;
    /** 单据状态持久化码。 */
    private Integer statusEnum;
}
