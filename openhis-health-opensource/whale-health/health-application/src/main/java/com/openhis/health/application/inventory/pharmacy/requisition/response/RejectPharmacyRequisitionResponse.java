/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.requisition.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 审批驳回药房请领响应（IF-PHA-QL-09）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "审批驳回药房请领 IF-PHA-QL-09")
public class RejectPharmacyRequisitionResponse {

    @Schema(description = "是否成功")
    /** 是否成功。 */
    private Boolean success;
}
