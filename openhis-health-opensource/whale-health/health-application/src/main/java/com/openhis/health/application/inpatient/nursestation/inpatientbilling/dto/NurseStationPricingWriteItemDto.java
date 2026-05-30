/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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

    /** 就诊主键。 */
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者主键。 */
    @Schema(description = "患者主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 账户主键（患者列表回传，BR-IP-PRC-013）。 */
    @Schema(description = "账户主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    /** 产品物理表：wkf_activity_definition / adm_device_definition。 */
    @Schema(description = "产品物理表", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productTableName;

    /** 产品定义主键。 */
    @Schema(description = "产品定义主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long productDefinitionId;

    /** 诊疗活动定义 id（诊疗行必填）。 */
    @Schema(description = "诊疗活动定义 id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityId;

    /** 耗材设备定义 id（耗材行必填）。 */
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

    /** 耗材发放库房（耗材行必填；未传时服务端按就诊住院科室与取药/发材模板解析，BR-IP-PRC-009）。 */
    @Schema(description = "耗材发放库房（可选；未传时服务端自动解析）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long performLocation;

    /** 父锚点：直接划价须为空；补费须非空。 */
    @Schema(description = "父锚点")
    private NurseStationPricingParentRequestAnchorDto parentRequestAnchor;

    /** 是否吸氧/心电计时类（选品回传，BR-IP-PRC-010 Gate 用）。 */
    @Schema(description = "是否吸氧/心电计时类")
    private Boolean oxygenOrEcgFlag;

    /** 计时 Gate 类型：{@code OXYGEN} / {@code ECG}（选品回传）。 */
    @Schema(description = "吸氧/心电 Gate 类型：OXYGEN/ECG")
    private String durationGateKind;

}
