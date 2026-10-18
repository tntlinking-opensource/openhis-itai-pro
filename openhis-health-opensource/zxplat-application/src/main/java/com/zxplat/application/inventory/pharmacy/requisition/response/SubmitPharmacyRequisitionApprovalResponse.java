/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.pharmacy.requisition.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 提交药房请领审批响应（IF-PHA-QL-06）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "提交药房请领审批 IF-PHA-QL-06")
public class SubmitPharmacyRequisitionApprovalResponse {

    @Schema(description = "是否成功")
    /** 是否成功。 */
    private Boolean success;

    /** 提交后状态 SupplyStatus#getCode。 */
    @Schema(description = "提交后状态 SupplyStatus#getCode")
    private String statusEnum;
}
