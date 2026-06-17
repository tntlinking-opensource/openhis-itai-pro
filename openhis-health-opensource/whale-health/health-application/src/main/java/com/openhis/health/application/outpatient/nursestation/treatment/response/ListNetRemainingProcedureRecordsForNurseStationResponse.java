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

package com.openhis.health.application.outpatient.nursestation.treatment.response;

import com.openhis.health.application.outpatient.nursestation.treatment.dto.NetRemainingProcedureRecordItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 净剩可取消执行记录列表响应（IF-NUR-03B）。
 */
@Data
@Accessors(chain = true)
/** 净剩可取消执行记录列表响应。 */
@Schema(description = "净剩可取消执行记录列表响应")
public class ListNetRemainingProcedureRecordsForNurseStationResponse {

    /**
     * 净剩 {@code COMPLETED} 行（已对冲的 COMPLETED/CANCEL 均不出现）。
     */
    @Schema(description = "净剩可取消执行记录列表")
    private List<NetRemainingProcedureRecordItemDto> records;
}
