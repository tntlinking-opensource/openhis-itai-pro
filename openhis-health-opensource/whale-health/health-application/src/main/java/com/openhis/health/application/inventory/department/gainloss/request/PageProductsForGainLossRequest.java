/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.gainloss.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.EqualsAndHashCode;

/**
 * 损益选品分页入参（IF-DEPT-GL-11）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "损益选品 IF-DEPT-GL-11")
public class PageProductsForGainLossRequest extends AbstractHealthPageRequest {

    /** 损益科室药房 purposeLocationId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "损益科室药房 purposeLocationId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long purposeLocationId;

    /** 产品表 productTableName。 */
    @Schema(description = "产品表 productTableName", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productTableName;
}
