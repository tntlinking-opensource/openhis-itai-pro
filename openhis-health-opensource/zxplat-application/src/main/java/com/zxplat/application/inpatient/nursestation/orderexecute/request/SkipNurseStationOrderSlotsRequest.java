/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.orderexecute.request;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.zxplat.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecuteWriteSlotDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 批量登记计划点不执行请求（IF-IP-EXE-04）。
 *
 * <p>仅 pending 槽位可登记；写 Procedure STOPPED，不做库存 Gate，不要求 actualTime。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量登记计划点不执行请求", requiredProperties = {"items"})
public class SkipNurseStationOrderSlotsRequest {

    /** 待不执行槽位列表。 */
    @NotEmpty(message = "请勾选至少一条待执行槽位")
    @Valid
    @Schema(description = "待不执行槽位列表", required = true)
    private List<NurseStationOrderExecuteWriteSlotDto> items;

    /** 预留；写路径不使用，仅列表查询用于展示窗口。 */
    @Schema(description = "预留；写路径不使用，仅列表查询用于展示窗口")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime executionDeadline;
}
