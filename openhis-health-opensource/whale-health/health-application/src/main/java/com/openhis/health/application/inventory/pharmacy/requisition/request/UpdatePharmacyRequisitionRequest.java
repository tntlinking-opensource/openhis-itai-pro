/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.requisition.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inventory.pharmacy.requisition.dto.PharmacyRequisitionLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 编辑药房请领单请求（IF-PHA-QL-05）。
 *
 * <p>可编辑态下全量替换批号级明细；请领申请单无 {@code original_bus_no} 上游引用语义。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(
    description = "编辑药房请领单 IF-PHA-QL-05",
    requiredProperties = {"busNo", "purposeLocationId", "sourceLocationId", "categoryEnum", "practitionerId", "lines"}
)
public class UpdatePharmacyRequisitionRequest {

    @NotBlank(message = "busNo 不能为空")
    /** 业务单号。 */
    @Schema(description = "业务单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    @NotNull(message = "purposeLocationId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 目的药房主键。 */
    @Schema(description = "目的药房主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long purposeLocationId;

    @NotNull(message = "sourceLocationId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 来源药库主键。 */
    @Schema(description = "来源药库主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sourceLocationId;

    /** 源位置类型 SupplyRequestType#getCode，可空由后端按库位推导。 */
    @Schema(description = "源位置类型 SupplyRequestType#getCode，可空由后端按库位推导")
    private String sourceTypeEnum;

    /** 目的位置类型 SupplyRequestType#getCode，可空由后端按库位推导。 */
    @Schema(description = "目的位置类型 SupplyRequestType#getCode，可空由后端按库位推导")
    private String purposeTypeEnum;

    @NotNull(message = "categoryEnum 不能为空")
    /** 供应类别 SupplyCategory#getCode。 */
    @Schema(description = "供应类别 SupplyCategory#getCode", requiredMode = Schema.RequiredMode.REQUIRED)
    private String categoryEnum;

    @NotNull(message = "经手人不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 经手人 practitionerId。 */
    @Schema(description = "经手人 practitionerId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long practitionerId;

    @NotEmpty(message = "明细不能为空")
    /** 批号级明细。 */
    @Schema(description = "批号级明细", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<PharmacyRequisitionLineDto> lines;
}
