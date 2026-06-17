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

package com.openhis.health.application.inpatient.nursestation.encountermanage.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 在院患者列表分页查询请求（IF-IP-NUR-02）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
/** 在院患者列表分页查询请求。 */
@Schema(description = "在院患者列表分页查询请求", requiredProperties = {"pageNo", "pageSize"})
public class PagePatientQueueRequest extends AbstractHealthPageRequest {

    @Schema(description = "病区位置主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long wardId;

    /** 病房位置主键；与 wardId 同时传时以病房为准。 */
    @Schema(description = "病房位置主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long houseId;

    /** 任务筛选 code（{@link com.openhis.health.domain.share.constants.InpatientNurseStationConstants}）。 */
    @Schema(description = "任务筛选：pending-audit / pending-execute / processed")
    private String nurseStationTaskFilter;

    /** 待执行计数自然日；空则按当天。 */
    @Schema(description = "待执行计数自然日")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate nurseStationExecutionDate;
}
