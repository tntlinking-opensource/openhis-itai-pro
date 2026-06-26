/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.pharmacy.dispenserecord.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 住院发退药记录列表行 DTO（IF-IPR-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院发退药记录列表行")
public class InpatientPharmacyDispenseRecordRowDto {

    /** 发放单主键。 */
    @Schema(description = "发放单主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;

    /** 记录类型 code：DISPENSE / REFUND。 */
    @Schema(description = "记录类型 code")
    private String recordTypeCode;

    /** 记录类型展示文本。 */
    @Schema(description = "记录类型展示")
    private String recordTypeText;

    /** 发药时间（yyyy-MM-dd HH:mm:ss）。 */
    @Schema(description = "发药时间")
    private String dispenseTime;

    /** 药房库位主键。 */
    @Schema(description = "药房主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long pharmacyLocationId;

    /** 药房名称。 */
    @Schema(description = "药房名称")
    private String pharmacyLocationName;

    /** 发药人主键。 */
    @Schema(description = "发药人主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenserPractitionerId;

    /** 发药人姓名。 */
    @Schema(description = "发药人姓名")
    private String dispenserName;

    /** 患者主键。 */
    @Schema(description = "患者主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 就诊主键。 */
    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 住院号。 */
    @Schema(description = "住院号")
    private String inpatientNo;

    /** 病区名称。 */
    @Schema(description = "病区名称")
    private String wardName;

    /** 床号。 */
    @Schema(description = "床号")
    private String bedName;

    /** 处方号。 */
    @Schema(description = "处方号")
    private String prescriptionNo;

    /** 药品定义主键。 */
    @Schema(description = "药品主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medicationId;

    /** 药品编码。 */
    @Schema(description = "药品编码")
    private String medicationCode;

    /** 药品名称。 */
    @Schema(description = "药品名称")
    private String medicationName;

    /** 规格展示。 */
    @Schema(description = "规格")
    private String medicationSpec;

    /** 数量（绝对值）。 */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /** 单位 code。 */
    @Schema(description = "单位 code")
    private String unitCode;

    /** 单位展示。 */
    @Schema(description = "单位展示")
    private String unitText;

    /** 单价；无 charge 为空。 */
    @Schema(description = "单价")
    private BigDecimal unitPrice;

    /** 金额；退药为负；无 charge 为空。 */
    @Schema(description = "金额")
    private BigDecimal totalAmount;

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 追溯码。 */
    @Schema(description = "追溯码")
    private String traceNo;

    /** 开单科室主键。 */
    @Schema(description = "开单科室主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderingDepartmentId;

    /** 开单科室名称。 */
    @Schema(description = "开单科室名称")
    private String orderingDepartmentName;

    /** 用法用量摘要。 */
    @Schema(description = "用法用量")
    private String usageText;

    /** 汇总单号。 */
    @Schema(description = "汇总单号")
    private String summaryNo;

    /** 药嘱主键。 */
    @Schema(description = "药嘱主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medReqId;
}
