/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.encountermanage.response;

import com.openhis.health.application.inpatient.nursestation.encountermanage.dto.NurseStationWardTreeNodeDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 病区病房树查询响应（IF-IP-NUR-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "病区病房树查询响应")
public class LoadWardTreeResponse {

    @Schema(description = "病区根节点列表")
    /** 病区根节点列表。 */
    private List<NurseStationWardTreeNodeDto> wardNodes;
}
