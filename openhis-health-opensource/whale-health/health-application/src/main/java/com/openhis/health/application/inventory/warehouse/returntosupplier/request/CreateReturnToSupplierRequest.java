/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.returntosupplier.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inventory.warehouse.returntosupplier.dto.ReturnToSupplierLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 新建药库退供应商单请求（IF-WHS-RT-04）。
 *
 * <p>{@code practitionerId} 为经手人医务人员主键，须显式传入；{@code applicant_id} 由服务端绑定当前登录医务人员。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(
    description = "新建药库退供应商单 IF-WHS-RT-04",
    requiredProperties = {"sourceLocationId", "purposeLocationId", "categoryEnum", "supplierId", "practitionerId", "lines"}
)
public class CreateReturnToSupplierRequest {

    /** 来源单号 originalBusNo。 */
    @Schema(description = "来源单号 originalBusNo")
    private String originalBusNo;

    /** 源药库 sourceLocationId。 */
    @NotNull(message = "sourceLocationId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "源药库 sourceLocationId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sourceLocationId;

    /** 目的位置 purposeLocationId。 */
    @NotNull(message = "purposeLocationId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "目的位置 purposeLocationId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long purposeLocationId;

    /** 源位置类型 sourceTypeEnum。 */
    @Schema(description = "源位置类型 sourceTypeEnum")
    private Integer sourceTypeEnum;

    /** 目的位置类型 purposeTypeEnum。 */
    @Schema(description = "目的位置类型 purposeTypeEnum")
    private Integer purposeTypeEnum;

    /** 供应类别 categoryEnum。 */
    @NotNull(message = "categoryEnum 不能为空")
    @Schema(description = "供应类别 categoryEnum", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer categoryEnum;

    /** 供应商 supplierId。 */
    @NotNull(message = "supplierId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "供应商 supplierId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long supplierId;

    /** 经手人 practitionerId。 */
    @NotNull(message = "practitionerId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "经手人 practitionerId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long practitionerId;

    /**
     * 采购发票号（可空；空时 3504 上报可回落进货单或占位 {@code 0}）。
     */
    @Schema(description = "采购发票号 invoiceNo")
    private String invoiceNo;

    /** 明细行 lines。 */
    @NotEmpty(message = "明细不能为空")
    @Schema(description = "明细行 lines", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ReturnToSupplierLineDto> lines;
}
