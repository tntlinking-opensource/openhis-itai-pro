/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingWriteItemDto;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IF-IP-PRC-02：直接划价确认请求（BR-IP-PRC-004）。
 *
 * <p>每行 {@link NurseStationPricingWriteItemDto#getParentRequestAnchor()} 须为空；整批 1～50 行同事务落库。
 * 可选 {@code pricingTime}：整批客户可见划价时刻（诊=Procedure.recorded；材=发放/拣配展示轴）；空则取服务端当前时刻。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "直接划价确认请求")
public class ConfirmDirectNurseStationPricingRequest {

    /** 划价行列表（1～50，禁止 parentRequestAnchor）。 */
    @NotEmpty(message = "划价行不能为空")
    @Schema(description = "划价行列表（1～50，禁止 parentRequestAnchor）", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<NurseStationPricingWriteItemDto> items;

    /**
     * 整批划价客户可见时刻（展示轴）；空则服务端 {@code LocalDateTime.now()}。
     */
    @Schema(description = "整批划价客户可见时刻；空则取当前时刻")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime pricingTime;
}
