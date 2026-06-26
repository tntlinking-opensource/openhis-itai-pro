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

package com.openhis.health.application.inpatient.nursestation.inpatientbilling.response;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * IF-IP-ARG-08：住院滚费定时任务响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院滚费定时任务响应")
public class ExecuteInpatientAutoRollJobResponse {

    /** 实际处理的滚费业务日。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    @Schema(description = "滚费业务日")
    private LocalDate billDate;

    /** 扫描 encounter 数。 */
    @Schema(description = "扫描就诊数")
    private Integer scannedEncounterCount;

    /** 至少落账 1 条的 encounter 数。 */
    @Schema(description = "成功处理就诊数")
    private Integer processedEncounterCount;

    /** 新建 ChargeItem 条数。 */
    @Schema(description = "新建费用条数")
    private Integer createdChargeItemCount;

    /** 幂等/边界跳过条数。 */
    @Schema(description = "跳过条数")
    private Integer skippedCount;

    /** 单条落账失败条数。 */
    @Schema(description = "失败条数")
    private Integer failedCount;
}
