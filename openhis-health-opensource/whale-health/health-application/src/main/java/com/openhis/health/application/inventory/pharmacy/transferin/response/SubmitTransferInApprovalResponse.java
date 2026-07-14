/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.transferin.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 提交药房调入审批响应（IF-PHA-TI-06）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "提交药房调入审批 IF-PHA-TI-06")
public class SubmitTransferInApprovalResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;

    /** 提交后状态 SupplyStatus#getCode。 */
    @Schema(description = "提交后状态 SupplyStatus#getCode")
    private String statusEnum;
}
