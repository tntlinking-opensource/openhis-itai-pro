/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.advice.response;

import com.openhis.health.application.outpatient.doctorstation.advice.dto.ServiceApplyFormHeaderDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DOR-22 / IF-IP-DOR-24：申请单列表响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "申请单列表响应")
public class ListDoctorStationServiceApplyFormsResponse {

    /** 按 prescriptionNo 分组的申请单。 */
    @Schema(description = "按 prescriptionNo 分组的申请单")
    private List<ServiceApplyFormHeaderDto> forms;
}
