/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.inpatientbilling.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingWriteItemDto;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IF-IP-PRC-04：医嘱补费确认请求（BR-IP-PRC-005）。
 *
 * <p>每行须带 {@link com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingParentRequestAnchorDto}；
 * 父医嘱须医生开立且状态 VERIFIED/STOPPED。
 * 可选 {@code pricingTime}：同 IF-02，整批客户可见划价时刻。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医嘱补费确认请求")
public class ConfirmSupplementNurseStationPricingRequest {

    /** 补费行列表（每行须 parentRequestAnchor）。 */
    @NotEmpty(message = "补费行不能为空")
    @Schema(description = "补费行列表（每行须 parentRequestAnchor）", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<NurseStationPricingWriteItemDto> items;

    /**
     * 整批划价客户可见时刻（展示轴）；空则服务端 {@code LocalDateTime.now()}。
     */
    @Schema(description = "整批划价客户可见时刻；空则取当前时刻")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime pricingTime;
}
