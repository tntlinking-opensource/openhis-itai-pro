/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.surgeryanesth.response;

import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.PlannedSurgeryFieldsDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * AS-IP-DOR-012 响应：GetPlannedSurgeryFields。
 */
@Data
@Accessors(chain = true)
@Schema(description = "GetPlannedSurgeryFieldsResponse")
public class GetPlannedSurgeryFieldsResponse {

    /** 计划字段。 */
    @Schema(description = "计划字段")
    private PlannedSurgeryFieldsDto planned;
}
