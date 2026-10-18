/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 手术申请单头（AS-IP-DOR-012 IF-IP-SUR-02/03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "手术申请单头")
public class SurgeryApplyFormHeaderDto {

    /** 申请单号（AR 前缀）。 */
    @Schema(description = "申请单号")
    private String prescriptionNo;

    /** 聚合状态：组内统一为 RequestStatus code；不一致为 MIXED。 */
    @Schema(description = "整单聚合状态")
    private String statusEnum;

    /** 拟手术时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "拟手术时间")
    private LocalDateTime plannedProcedureTime;

    /** 主术式展示名。 */
    @Schema(description = "主术式名称")
    private String mainProcedureName;

    /** 行数。 */
    @Schema(description = "行数")
    private Integer lineCount;

    /** 嵌套麻醉申请单号。 */
    @Schema(description = "嵌套麻醉申请单号")
    private String nestedAnesthesiaPrescriptionNo;

    /** 开单医生姓名。 */
    @Schema(description = "开单医生姓名")
    private String requesterName;
}
