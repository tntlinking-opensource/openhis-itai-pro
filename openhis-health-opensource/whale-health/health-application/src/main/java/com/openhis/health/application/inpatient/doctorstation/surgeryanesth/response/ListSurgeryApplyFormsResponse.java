/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.surgeryanesth.response;

import com.openhis.health.application.inpatient.doctorstation.surgeryanesth.dto.SurgeryApplyFormHeaderDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * AS-IP-DOR-012 响应：ListSurgeryApplyForms。
 */
@Data
@Accessors(chain = true)
@Schema(description = "ListSurgeryApplyFormsResponse")
public class ListSurgeryApplyFormsResponse {

    /** 手术申请单头列表。 */
    @Schema(description = "手术申请单头列表")
    private List<SurgeryApplyFormHeaderDto> forms;
}
