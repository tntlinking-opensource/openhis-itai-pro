/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.surgeryanesth.response;

import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.AnesthesiaApplyFormHeaderDto;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.AnesthesiaApplyFormLineDto;
import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.PlannedSurgeryFieldsDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * AS-IP-DOR-012 响应：GetAnesthesiaApplyFormDetail。
 */
@Data
@Accessors(chain = true)
@Schema(description = "GetAnesthesiaApplyFormDetailResponse")
public class GetAnesthesiaApplyFormDetailResponse {

    /** 单头。 */
    @Schema(description = "单头")
    private AnesthesiaApplyFormHeaderDto header;

    /** 行列表。 */
    @Schema(description = "行列表")
    private List<AnesthesiaApplyFormLineDto> lines;

    /** 计划字段。 */
    @Schema(description = "计划字段")
    private PlannedSurgeryFieldsDto planned;
}
