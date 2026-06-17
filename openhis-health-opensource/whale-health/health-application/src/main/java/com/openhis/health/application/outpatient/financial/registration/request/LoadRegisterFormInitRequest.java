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

package com.openhis.health.application.outpatient.financial.registration.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 挂号初始化请求。
 */
@Data
@Accessors(chain = true)
/** 挂号初始化请求。 */
@Schema(description = "挂号初始化请求")
public class LoadRegisterFormInitRequest {

    @Schema(description = "科室ID")
    /** 科室ID（业务字段）。 */
    private Long organizationId;

    /** 医师 ID（号源收窄）。 */
    @Schema(description = "医师ID")
    /** 医师ID（业务字段）。 */
    private Long practitionerId;

    /** 挂号自然日（号源收窄，默认当日）。 */
    @Schema(description = "挂号自然日")
    /** 挂号自然日（业务字段）。 */
    private LocalDate registrationDate;

    /** 检索关键字。 */
    @Schema(description = "关键字")
    /** 关键字（业务字段）。 */
    private String keyword;
}
