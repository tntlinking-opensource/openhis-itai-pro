/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.orderexecute.request;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecuteWriteSlotDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 批量执行医嘱计划点请求（IF-IP-EXE-02）。
 *
 * <p>每条 item 须含 orderId、orderType、planTime、actualTime；bindItems 为列表勾选的绑定项子集。
 * 服务端同事务处理，库存 Gate 仅药嘱，绑定耗材不参与 Gate/扣减。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量执行医嘱计划点请求", requiredProperties = {"items"})
public class ExecuteNurseStationOrdersRequest {

    /** 待执行槽位列表。 */
    @NotEmpty(message = "请勾选至少一条待执行槽位")
    @Valid
    @Schema(description = "待执行槽位列表", required = true)
    private List<NurseStationOrderExecuteWriteSlotDto> items;

    /**
     * 预留字段：写路径 Gate 不使用；列表 IF-IP-EXE-01 专用展示窗口上界。
     */
    @Schema(description = "预留；写路径不使用，仅列表查询用于展示窗口")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 预留；写路径不使用，仅列表查询用于展示窗口。 */
    private LocalDateTime executionDeadline;
}
