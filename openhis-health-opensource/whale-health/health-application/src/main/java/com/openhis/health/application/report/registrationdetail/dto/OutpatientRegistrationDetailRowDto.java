/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.registrationdetail.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.whale.core.application.annotation.Excel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 门诊挂号明细报表明细行（IF-RPT-ORG-03/04；含费用性质 enrich）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊挂号明细报表明细行")
public class OutpatientRegistrationDetailRowDto {

    /** 就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键")
    private Long encounterId;

    /** 患者主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者主键")
    private Long patientId;

    /** 患者姓名。 */
    @Excel(name = "患者姓名", sort = 1)
    @Schema(description = "患者姓名")
    private String patientName;

    /** 门诊号。 */
    @Excel(name = "门诊号", sort = 2)
    @Schema(description = "门诊号")
    private String busNo;

    /** 挂号科室主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "挂号科室主键")
    private Long organizationId;

    /** 挂号科室名称。 */
    @Excel(name = "挂号科室", sort = 3)
    @Schema(description = "挂号科室名称")
    private String organizationName;

    /** 挂号医生主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "挂号医生主键")
    private Long registrationDoctorId;

    /** 挂号医生姓名。 */
    @Excel(name = "挂号医生", sort = 4)
    @Schema(description = "挂号医生姓名")
    private String registrationDoctorName;

    /** 号源类型主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "号源类型主键")
    private Long serviceTypeId;

    /** 号源类型名称。 */
    @Excel(name = "号源类型", sort = 5)
    @Schema(description = "号源类型名称")
    private String serviceTypeName;

    /** 就诊状态码。 */
    @Schema(description = "就诊状态码")
    private String statusEnum;

    /** 就诊状态展示。 */
    @Excel(name = "状态", sort = 6)
    @Schema(description = "就诊状态展示")
    private String statusEnum_enumText;

    /** 挂号发生时刻。 */
    @Excel(name = "挂号时间", sort = 7)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "挂号时间")
    private LocalDateTime registrationTime;

    /** 费用性质合同业务号（fin_contract.bus_no）。 */
    @Schema(description = "费用性质合同业务号")
    private String feeNatureContractNo;

    /** 费用性质展示（自费/医保合同名；BR-RPT-ORG-004）。 */
    @Excel(name = "费用性质", sort = 8)
    @Schema(description = "费用性质展示")
    private String feeNatureDisplay;

    /** 费用性质分类码（ContractCategory）。 */
    @Schema(description = "费用性质分类码")
    private String feeNatureCategoryCode;

    /** 医保类型展示（非自费时有值）。 */
    @Excel(name = "医保类型", sort = 9)
    @Schema(description = "医保类型展示")
    private String insuranceTypeDisplay;

    /** 挂号费金额（号源锚点 chargeItem 合计）。 */
    @Excel(name = "挂号费", sort = 10)
    @Schema(description = "挂号费金额")
    private BigDecimal registrationFeeAmount;
}
