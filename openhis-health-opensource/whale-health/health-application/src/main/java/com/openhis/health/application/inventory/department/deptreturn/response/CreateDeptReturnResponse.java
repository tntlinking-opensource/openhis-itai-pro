/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.department.deptreturn.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateDeptReturnResponse {

    /** 业务单号。 */
    private String busNo;
    /** 单据状态持久化码。 */
    private Integer statusEnum;
}
