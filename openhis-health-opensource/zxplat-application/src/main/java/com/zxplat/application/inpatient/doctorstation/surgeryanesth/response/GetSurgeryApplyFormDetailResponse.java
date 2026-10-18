/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.surgeryanesth.response;

import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.PlannedSurgeryFieldsDto;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.SurgeryApplyFormHeaderDto;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.SurgeryApplyFormLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * AS-IP-DOR-012 响应：GetSurgeryApplyFormDetail。
 */
@Data
@Accessors(chain = true)
@Schema(description = "GetSurgeryApplyFormDetailResponse")
public class GetSurgeryApplyFormDetailResponse {

    /** 单头。 */
    @Schema(description = "单头")
    private SurgeryApplyFormHeaderDto header;

    /** 行列表。 */
    @Schema(description = "行列表")
    private List<SurgeryApplyFormLineDto> lines;

    /** 计划字段。 */
    @Schema(description = "计划字段")
    private PlannedSurgeryFieldsDto planned;
}
