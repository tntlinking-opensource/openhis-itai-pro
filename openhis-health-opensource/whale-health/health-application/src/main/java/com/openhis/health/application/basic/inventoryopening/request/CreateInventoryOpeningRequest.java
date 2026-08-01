/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.inventoryopening.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.basic.inventoryopening.dto.InventoryOpeningLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 新建期初单请求（IF-BAS-INV-OPN-04）；lines 可空（空草稿合法）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "新建期初单", requiredProperties = {"locationId", "occurrenceTime"})
public class CreateInventoryOpeningRequest {

    /** 目标库房主键 → purpose_location_id。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库房主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long locationId;

    /** 经手人医务人员主键（可选）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "经手人")
    private Long practitionerId;

    /** 业务发生时点。 */
    @Schema(description = "业务时点", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime occurrenceTime;

    /** 明细行；可空列表表示空草稿。 */
    @Schema(description = "明细行")
    private List<InventoryOpeningLineDto> lines;
}
