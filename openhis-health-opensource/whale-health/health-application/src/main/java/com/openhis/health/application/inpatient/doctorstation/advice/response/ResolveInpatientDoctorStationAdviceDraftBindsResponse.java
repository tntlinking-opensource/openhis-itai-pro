/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.response;

import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceDraftBindItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-DOR-23：住院暂存前绑定解析响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院医生站-暂存前绑定解析响应")
public class ResolveInpatientDoctorStationAdviceDraftBindsResponse {

    /** 绑定明细（合管优先；同批同设备去重）。 */
    @Schema(description = "绑定明细")
    private List<DoctorStationAdviceDraftBindItemDto> bindItems;
}
