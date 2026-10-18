/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.inpatientbilling.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingCancelItemDto;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IF-IP-PRC-07：修改划价客户可见时间请求。
 *
 * <p>诊疗改 Procedure.recorded_time；耗材改发放/拣配展示时刻；不改 occurrence / Charge.occurrence。
 * 行键 DTO 与冲销 {@link NurseStationPricingCancelItemDto} 同构。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "修改划价展示时间请求")
public class ChangeNurseStationPricingBusinessTimeRequest {

    /** 改时行列表（1～50；与已划价列表勾选行一致）。 */
    @NotEmpty(message = "改时划价行不能为空")
    @Schema(description = "改时划价行列表（1～50）", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<NurseStationPricingCancelItemDto> items;

    /** 新的客户可见时刻（统一写入各行展示轴）。 */
    @NotNull(message = "新时间不能为空")
    @Schema(description = "新的客户可见时刻", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime newTime;
}
