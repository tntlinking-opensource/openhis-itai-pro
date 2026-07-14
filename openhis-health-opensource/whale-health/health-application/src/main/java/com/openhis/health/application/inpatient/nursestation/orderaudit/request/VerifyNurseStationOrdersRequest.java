/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.orderaudit.request;

import com.openhis.health.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderAuditWriteItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量校对通过请求（IF-IP-AUD-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量校对通过请求", requiredProperties = {"items"})
public class VerifyNurseStationOrdersRequest {

    /** 待校对医嘱行（1～50 条）。 */
    @NotEmpty(message = "校对项不能为空")
    @Valid
    @Schema(description = "待校对医嘱行（1～50 条）", required = true)
    private List<NurseStationOrderAuditWriteItemDto> items;
}
