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

package com.openhis.health.application.emergency.emgtriage.station.response;

import com.openhis.health.application.emergency.emgtriage.station.dto.KnowledgeCandidateRowDto;
import com.openhis.whale.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * IF-EMG-13：知识库检索响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "急诊分诊-知识库检索响应")
public class SearchKnowledgeForEmergencyTriageResponse {

    @Schema(description = "分页候选")
    private PagedResult<KnowledgeCandidateRowDto> page;

    @Schema(description = "TopN 候选")
    private List<KnowledgeCandidateRowDto> topN = new ArrayList<>();
}
