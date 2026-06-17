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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 分床/入科责任医护下拉初始化请求。
 */
@Data
@Accessors(chain = true)
/** 分床责任医护下拉初始化请求。 */
@Schema(description = "分床责任医护下拉初始化请求")
public class LoadAdmissionParticipantInitRequest {

    @Schema(description = "入院科室主键；有值时按科室岗位分配收窄住院医师")
    private Long organizationId;

    @Schema(description = "入院病区位置主键；有值时按病区岗位分配收窄责任护士")
    private Long wardLocationId;
}
