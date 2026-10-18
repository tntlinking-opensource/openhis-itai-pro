/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.dispensesummary.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 汇总领药候选明细行（护士站汇总发药 Tab 勾选池；IF-IP-SUM 同族）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "汇总领药候选明细行")
public class NurseStationDispensePickupLineDto {

    /** 发放单主键。 */
    @Schema(description = "发放单主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;

    /** 就诊主键。 */
    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 床号。 */
    @Schema(description = "床号")
    private String bedNo;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 住院号。 */
    @Schema(description = "住院号")
    private String encounterBusNo;

    /** 药品名称。 */
    @Schema(description = "药品名称")
    private String drugName;

    /** 数量。 */
    @Schema(description = "数量")
    private String quantity;

    /** 单位。 */
    @Schema(description = "单位")
    private String unitText;

    /** 长临嘱。 */
    @Schema(description = "长临嘱")
    private String orderClass;

    /** 是否中药医嘱（true=中药 Tab）。 */
    @Schema(description = "是否中药医嘱（true=中药 Tab）")
    private Boolean tcmFlag;

    /** 药房。 */
    @Schema(description = "药房")
    private String pharmacyName;

    /** 状态码。 */
    @Schema(description = "状态码")
    private String statusEnum;

    /** 状态文案。 */
    @Schema(description = "状态文案")
    private String statusEnumText;
}
