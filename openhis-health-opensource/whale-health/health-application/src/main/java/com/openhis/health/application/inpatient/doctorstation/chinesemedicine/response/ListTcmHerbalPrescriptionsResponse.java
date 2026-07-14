/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.chinesemedicine.response;

import com.openhis.health.application.inpatient.doctorstation.chinesemedicine.dto.TcmRxHeaderDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * IF-IP-TCM-34：中药处方列表响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "中药处方列表响应")
public class ListTcmHerbalPrescriptionsResponse {

    /** 按方聚合的处方列表。 */
    @Schema(description = "按方聚合的处方列表")
    private List<TcmRxHeaderDto> prescriptions = new ArrayList<>();
}
