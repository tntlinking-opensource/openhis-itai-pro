/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 护士划价写路径单行（IF-IP-PRC-02/04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "护士划价写路径单行")
public class NurseStationPricingWriteItemDto {

    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    @Schema(description = "患者主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 账户主键。 */
    @Schema(description = "账户主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    /** 产品物理表。 */
    @Schema(description = "产品物理表", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productTableName;

    /** 产品定义主键。 */
    @Schema(description = "产品定义主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long productDefinitionId;

    /** 诊疗活动定义 id。 */
    @Schema(description = "诊疗活动定义 id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityId;

    /** 耗材设备定义 id。 */
    @Schema(description = "耗材设备定义 id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deviceDefId;

    /** 数量。 */
    @Schema(description = "数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal quantity;

    /** 单位 code。 */
    @Schema(description = "单位 code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unitCode;

    /** 收费项定义 id。 */
    @Schema(description = "收费项定义 id", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long chargeItemDefinitionId;

    /** 单价。 */
    @Schema(description = "单价", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal unitPrice;

    /** 总价。 */
    @Schema(description = "总价", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal totalPrice;

    /**
     * 耗材发放库房（IF-IP-PRC-01 目录已解析；写路径定价 FIFO 直接在该库位取批号，不再二次 match 模板）。
     */
    @Schema(description = "耗材发放库房（目录 defaultDispenseLocationId 回传；耗材行必填）")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 耗材发放库房（目录 defaultDispenseLocationId 回传；耗材行必填）。 */
    private Long performLocation;

    @Schema(description = "父锚点")
    /** 父锚点。 */
    private NurseStationPricingParentRequestAnchorDto parentRequestAnchor;

    @Schema(description = "是否吸氧/心电计时类")
    /** 是否吸氧/心电计时类。 */
    private Boolean oxygenOrEcgFlag;

    /** 吸氧/心电 Gate 类型：OXYGEN/ECG。 */
    @Schema(description = "吸氧/心电 Gate 类型：OXYGEN/ECG")
    private String durationGateKind;

}
