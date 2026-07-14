/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.response;

import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceOrderBindRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-DOR-12：查询医嘱绑定信息响应。
 *
 * <p>{@code rows} 为 {@link InpatientDoctorStationAdviceOrderBindRowDto} 列表，条数受领域常量上限约束。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-医嘱开立-查询医嘱绑定信息响应")
public class GetInpatientDoctorStationAdviceOrderBindInfoResponse {

    @Schema(description = "绑定明细")
    /** 绑定明细。 */
    private List<InpatientDoctorStationAdviceOrderBindRowDto> bindRows;
}
