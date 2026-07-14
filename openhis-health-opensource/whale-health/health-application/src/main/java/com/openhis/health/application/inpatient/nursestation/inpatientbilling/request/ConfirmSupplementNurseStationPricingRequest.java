/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.request;

import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingWriteItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-PRC-04：医嘱补费确认请求（BR-IP-PRC-005）。
 *
 * <p>每行须带 {@link com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingParentRequestAnchorDto}；
 * 父医嘱须医生开立且状态 VERIFIED/STOPPED。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医嘱补费确认请求")
public class ConfirmSupplementNurseStationPricingRequest {

    /** 补费行列表（每行须 parentRequestAnchor）。 */
    @NotEmpty(message = "补费行不能为空")
    @Schema(description = "补费行列表（每行须 parentRequestAnchor）", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<NurseStationPricingWriteItemDto> items;
}
