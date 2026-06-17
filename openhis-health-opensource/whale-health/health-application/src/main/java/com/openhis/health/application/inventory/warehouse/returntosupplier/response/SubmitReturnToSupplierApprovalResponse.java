/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.warehouse.returntosupplier.response;

import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class SubmitReturnToSupplierApprovalResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功 success")
    private Boolean success;
    /** 单据状态持久化码。 */
    @Schema(description = "单据状态 statusEnum")
    private Integer statusEnum;
}
