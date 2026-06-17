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

package com.openhis.health.application.inpatient.nursestation.encountermanage.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 分床/入科责任医护下拉初始化响应。
 */
@Data
@Accessors(chain = true)
/** 分床责任医护下拉初始化响应。 */
@Schema(description = "分床责任医护下拉初始化响应")
public class LoadAdmissionParticipantInitResponse {

    @Schema(description = "住院医师/主治/主任医生候选")
    private List<OptionDto> doctorOptions;

    @Schema(description = "责任护士候选")
    private List<OptionDto> primaryNurseOptions;
}
