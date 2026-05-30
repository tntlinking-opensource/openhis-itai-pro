/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inpatient.nursestation.orderexecute.request;

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

    /** 待执行槽位列表（≤50，不得携带 procedureId）。 */
    @NotEmpty(message = "请勾选至少一条待执行槽位")
    @Valid
    @Schema(description = "待执行槽位列表", required = true)
    private List<NurseStationOrderExecuteWriteSlotDto> items;

    /**
     * 预留字段：写路径 Gate 不使用；列表 IF-IP-EXE-01 专用展示窗口上界。
     */
    @Schema(description = "预留；写路径不使用，仅列表查询用于展示窗口")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime executionDeadline;
}
