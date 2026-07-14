/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.request;

import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingCancelItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-PRC-06：取消划价请求（BR-IP-PRC-021～028）。
 *
 * <p>来自已划价列表多选；1～50 行同事务整行还原，非部分数量冲销。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "取消划价请求")
public class CancelNurseStationPricingRequest {

    /** 取消划价行列表（1～50）。 */
    @NotEmpty(message = "取消划价行不能为空")
    @Schema(description = "取消划价行列表（1～50）", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<NurseStationPricingCancelItemDto> items;
}
