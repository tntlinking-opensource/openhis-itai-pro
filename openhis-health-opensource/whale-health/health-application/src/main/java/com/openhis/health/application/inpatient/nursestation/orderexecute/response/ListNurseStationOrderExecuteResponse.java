/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.orderexecute.response;

import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecutePatientGroupDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 医嘱执行列表响应（IF-IP-EXE-01）。
 *
 * <p>{@code groups} 按 encounterId 分组；每组 {@code orders} 已按长临+开立时间排序，
 * 每行含四类槽位与 bindItems。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医嘱执行列表响应")
public class ListNurseStationOrderExecuteResponse {

    @Schema(description = "按就诊分组的医嘱行")
    /** 按就诊分组的医嘱行。 */
    private List<NurseStationOrderExecutePatientGroupDto> groups;
}
