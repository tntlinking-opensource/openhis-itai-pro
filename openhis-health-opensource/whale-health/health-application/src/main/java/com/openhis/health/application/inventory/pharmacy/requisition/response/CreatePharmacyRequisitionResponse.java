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
 * 新建药房请领单响应（IF-PHA-QL-04）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "新建药房请领单 IF-PHA-QL-04")
public class CreatePharmacyRequisitionResponse {

    @Schema(description = "业务单号")
    /** 业务单号。 */
    private String busNo;
}
