/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.advice.response;

import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceDraftBindItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DOR-23：暂存前绑定解析响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-医嘱开立-暂存前绑定解析响应")
public class ResolveDoctorStationAdviceDraftBindsResponse {

    /** 绑定明细。 */
    @Schema(description = "绑定明细")
    private List<DoctorStationAdviceDraftBindItemDto> bindItems;
}
