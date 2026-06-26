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

package com.openhis.health.application.inpatient.doctorstation.chinesemedicine.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * IF-DOR-39：患者历史中药处方摘要行。
 */
@Data
@Accessors(chain = true)
@Schema(description = "患者历史中药处方摘要")
public class TcmRxHistoryRowDto {

    /** 处方号。 */
    @Schema(description = "处方号")
    private String prescriptionNo;

    /** 来源就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "来源就诊主键")
    private Long encounterId;

    /** 开立时间。 */
    @Schema(description = "开立时间")
    private LocalDateTime authoredAt;

    /** 方摘要（药味数+付数）。 */
    @Schema(description = "方摘要")
    private String summaryText;

    /** 药名列表。 */
    @Schema(description = "药名列表")
    private List<String> medicationNames = new ArrayList<>();
}
