/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.orderaudit.response;

import com.openhis.health.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderAuditPatientGroupDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 医嘱校对列表查询响应（IF-IP-AUD-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医嘱校对列表查询响应")
public class ListNurseStationOrderAuditResponse {

    @Schema(description = "按 encounterId 分组的医嘱行")
    /** 按 encounterId 分组的医嘱行。 */
    private List<NurseStationOrderAuditPatientGroupDto> groups;
}
