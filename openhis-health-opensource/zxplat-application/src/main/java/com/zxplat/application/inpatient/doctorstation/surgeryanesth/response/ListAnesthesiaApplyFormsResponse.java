/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.surgeryanesth.response;

import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.AnesthesiaApplyFormHeaderDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * AS-IP-DOR-012 响应：ListAnesthesiaApplyForms。
 */
@Data
@Accessors(chain = true)
@Schema(description = "ListAnesthesiaApplyFormsResponse")
public class ListAnesthesiaApplyFormsResponse {

    /** 麻醉申请单头列表。 */
    @Schema(description = "麻醉申请单头列表")
    private List<AnesthesiaApplyFormHeaderDto> forms;
}
