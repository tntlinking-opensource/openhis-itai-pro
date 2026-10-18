/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.inventoryopening.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.basic.inventoryopening.dto.InventoryOpeningDetailLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 期初单详情响应（IF-BAS-INV-OPN-03）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "期初单详情响应")
public class GetInventoryOpeningDetailResponse {

    /** 业务单号。 */
    @Schema(description = "单号")
    private String busNo;

    /** OPENING 类型码。 */
    @Schema(description = "类型")
    private String typeEnum;

    /** 供应状态码。 */
    @Schema(description = "状态")
    private String statusEnum;

    /** 目标库房主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库房主键")
    private Long locationId;

    /** 库房名称。 */
    @Schema(description = "库房名称")
    private String locationName;

    /** 经手人主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "经手人")
    private Long practitionerId;

    /** 业务时点。 */
    @Schema(description = "业务时点")
    private LocalDateTime occurrenceTime;

    /** 申请时间。 */
    @Schema(description = "申请时间")
    private LocalDateTime applyTime;

    /** 审批时间。 */
    @Schema(description = "审批时间")
    private LocalDateTime approvalTime;

    /** 明细行。 */
    @Schema(description = "明细")
    private List<InventoryOpeningDetailLineDto> lines;
}
