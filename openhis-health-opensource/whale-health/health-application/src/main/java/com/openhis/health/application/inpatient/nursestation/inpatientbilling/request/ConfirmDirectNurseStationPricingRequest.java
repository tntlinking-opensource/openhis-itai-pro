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
 * IF-IP-PRC-02：直接划价确认请求（BR-IP-PRC-004）。
 *
 * <p>每行 {@link NurseStationPricingWriteItemDto#getParentRequestAnchor()} 须为空；整批 1～50 行同事务落库。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "直接划价确认请求")
public class ConfirmDirectNurseStationPricingRequest {

    /** 划价行列表（1～50，禁止 parentRequestAnchor）。 */
    @NotEmpty(message = "划价行不能为空")
    @Schema(description = "划价行列表（1～50，禁止 parentRequestAnchor）", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<NurseStationPricingWriteItemDto> items;
}
