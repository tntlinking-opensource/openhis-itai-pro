/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 麻醉申请单头（AS-IP-DOR-012 IF-IP-SUR-10/11）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "麻醉申请单头")
public class AnesthesiaApplyFormHeaderDto {

    /** 申请单号。 */
    @Schema(description = "申请单号")
    private String prescriptionNo;

    /** 聚合状态：组内统一为 RequestStatus code；不一致为 MIXED。 */
    @Schema(description = "整单聚合状态")
    private String statusEnum;

    /** 关联手术申请单号。 */
    @Schema(description = "关联手术申请单号")
    private String linkedSurgeryPrescriptionNo;

    /** 计划麻醉方法字典码。 */
    @Schema(description = "麻醉方法字典码")
    private String anesthesiaMethodCode;

    /** 行数。 */
    @Schema(description = "行数")
    private Integer lineCount;

    /** 开单人姓名。 */
    @Schema(description = "开单人姓名")
    private String requesterName;
}
