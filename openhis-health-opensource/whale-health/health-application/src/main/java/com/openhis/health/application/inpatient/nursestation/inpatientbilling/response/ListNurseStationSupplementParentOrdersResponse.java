/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.response;

import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingPatientGroupDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * IF-IP-PRC-03：医嘱补费父医嘱列表响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医嘱补费父医嘱列表响应")
public class ListNurseStationSupplementParentOrdersResponse {

    @Schema(description = "按就诊分组")
    /** 按就诊分组。 */
    private List<NurseStationPricingPatientGroupDto> groups = new ArrayList<>();
}
