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

package com.openhis.health.application.inpatient.nursestation.ordercancelexecute.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量取消已执行医嘱计划点响应（IF-IP-CXL-01）。
 */
@Data
@Accessors(chain = true)
/** 批量取消已执行医嘱计划点响应。 */
@Schema(description = "批量取消已执行医嘱计划点响应")
public class CancelNurseStationOrderExecutionsResponse {

    @Schema(description = "整批是否成功")
    private Boolean success;

    @Schema(description = "成功对冲的原 COMPLETED 执行记录主键列表")
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> cancelledProcedureIds;

    /** 过滤重建生成的新汇总单号；无重建时为空列表。 */
    @Schema(description = "过滤重建后的新汇总单号列表")
    private List<String> rebuiltSummaryNos;

    /** 成功提示；含重建时可提示已自动重建汇总单。 */
    @Schema(description = "成功提示文案")
    private String message;
}
