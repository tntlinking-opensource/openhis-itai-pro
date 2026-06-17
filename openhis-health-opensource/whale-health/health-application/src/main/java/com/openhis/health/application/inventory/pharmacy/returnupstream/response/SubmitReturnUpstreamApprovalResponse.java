/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.pharmacy.returnupstream.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmitReturnUpstreamApprovalResponse {

    /** 操作是否成功。 */
    private Boolean success;
    /** 单据状态持久化码。 */
    private Integer statusEnum;
}
