/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.stockin.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 审批驳回药房入库响应（IF-PHA-IN-09）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "审批驳回药房入库 IF-PHA-IN-09")
public class RejectPharmacyStockInResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;
}
