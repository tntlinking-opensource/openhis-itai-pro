/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.advice.response;

import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceOrderBindRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DOR-12：查询医嘱绑定信息响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-医嘱开立-查询医嘱绑定信息响应")
public class GetDoctorStationAdviceOrderBindInfoResponse {

    @Schema(description = "绑定明细")
    /** 绑定明细。 */
    private List<DoctorStationAdviceOrderBindRowDto> bindRows;
}
