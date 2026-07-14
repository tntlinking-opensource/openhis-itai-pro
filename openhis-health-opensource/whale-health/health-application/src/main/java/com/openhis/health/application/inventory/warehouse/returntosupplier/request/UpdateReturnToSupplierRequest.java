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
 * 修订药库退供应商单请求（IF-WHS-RT-05）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "修订药库退供应商单 IF-WHS-RT-05")
public class UpdateReturnToSupplierRequest {

    /** 业务单号 busNo。 */
    @NotNull(message = "busNo 不能为空")
    @Schema(description = "业务单号 busNo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

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

    /** 明细行 lines。 */
    @NotEmpty(message = "明细不能为空")
    @Schema(description = "明细行 lines", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ReturnToSupplierLineDto> lines;

    /**
     * 乐观锁版本（禁止客户端传入；传入时应用层返回 {@code WHS_RT_105}）。
     */
    @Schema(description = "已废弃：乐观锁由服务端维护", deprecated = true)
    private Integer rowVersion;
}
