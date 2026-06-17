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

package com.openhis.health.application.inpatient.pharmacy.returnmedicine.request;

import com.openhis.health.module.common.pagination.PageQueryBinder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分页查询住院退药患者队列请求（IF-IPR-02）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
/** 分页查询住院退药患者队列请求。 */
@Schema(description = "分页查询住院退药患者队列请求")
public class PageInpatientReturnPatientRequest extends PageQueryBinder.AbstractHealthPageRequest {

    private static final long serialVersionUID = 1L;

    @Schema(description = "病区过滤（ward locationId）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long wardId;

    /** 队列页签：PENDING=有待退；COMPLETED=仅已退且无待退；默认 PENDING。 */
    @Schema(description = "队列页签：PENDING=有待退；COMPLETED=仅已退且无待退")
    private String queueTab;
}
