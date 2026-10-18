/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.transferin.response;

import com.zxplat.application.inventory.warehouse.transferin.dto.TransferInDetailLineDto;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 药库调入单详情响应（IF-WHS-TI-03）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "药库调入单详情 IF-WHS-TI-03")
public class GetTransferInDetailResponse {

    /** 业务单号。 */
    @Schema(description = "业务单号")
    private String busNo;

    /** 类型 PHARMACY_TRANSFER_IN。 */
    @Schema(description = "类型 PHARMACY_TRANSFER_IN")
    private String typeEnum;

    /** 状态 SupplyStatus#getCode。 */
    @Schema(description = "状态 SupplyStatus#getCode")
    private String statusEnum;

    /** 来源位置主键。 */
    @Schema(description = "来源位置主键")
    private Long sourceLocationId;

    /** 目的位置主键。 */
    @Schema(description = "目的位置主键")
    private Long purposeLocationId;

    /** 源位置类型。 */
    @Schema(description = "源位置类型")
    private String sourceTypeEnum;

    /** 目的位置类型。 */
    @Schema(description = "目的位置类型")
    private String purposeTypeEnum;

    /** 供应类别。 */
    @Schema(description = "供应类别")
    private String categoryEnum;

    /** 关联上游业务单号。 */
    @Schema(description = "关联上游业务单号")
    private String originalBusNo;

    /** 经手人医务人员主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "经手人 practitionerId")
    private Long practitionerId;

    /** 经手人姓名（详情回显下拉用）。 */
    @Schema(description = "经手人姓名")
    private String practitionerName;


    /** 明细行。 */
    @Schema(description = "明细行")
    private List<TransferInDetailLineDto> lines;
}
