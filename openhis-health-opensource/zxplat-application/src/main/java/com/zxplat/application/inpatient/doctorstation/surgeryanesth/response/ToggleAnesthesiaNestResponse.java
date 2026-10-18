/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.surgeryanesth.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * AS-IP-DOR-012 响应：ToggleAnesthesiaNest。
 */
@Data
@Accessors(chain = true)
@Schema(description = "ToggleAnesthesiaNestResponse")
public class ToggleAnesthesiaNestResponse {

    /** 手术申请单号。 */
    @Schema(description = "手术申请单号")
    private String surgeryPrescriptionNo;

    /** 麻醉申请单号。 */
    @Schema(description = "麻醉申请单号")
    private String anesthesiaPrescriptionNo;

    /** 嵌套开关。 */
    @Schema(description = "嵌套开关")
    private Boolean enabled;
}
