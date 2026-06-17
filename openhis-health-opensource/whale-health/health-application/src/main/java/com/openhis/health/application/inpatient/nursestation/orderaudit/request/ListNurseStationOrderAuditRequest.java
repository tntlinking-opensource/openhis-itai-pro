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

package com.openhis.health.application.inpatient.nursestation.orderaudit.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 医嘱校对列表查询请求（IF-IP-AUD-01）。
 */
@Data
@Accessors(chain = true)
/** 医嘱校对列表查询请求。 */
@Schema(description = "医嘱校对列表查询请求", requiredProperties = {"encounterIds", "auditTab"})
public class ListNurseStationOrderAuditRequest {

    /** 左侧多选就诊主键列表（BR-IP-AUD-001）。 */
    @NotEmpty(message = "就诊标识列表不能为空")
    @Schema(description = "左侧多选就诊主键列表", required = true)
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> encounterIds;

    /** 校对 Tab：pending / passed / stopped / suspended（BR-IP-AUD-003）。 */
    @NotBlank(message = "校对 Tab 不能为空")
    /** 校对 Tab：pending / passed / stopped / suspended。 */
    @Schema(description = "校对 Tab：pending / passed / stopped / suspended", required = true)
    private String auditTab;

    /** 长临筛选：all / long / temp；默认 all（BR-IP-AUD-004）。 */
    @Schema(description = "长临筛选：all / long / temp；默认 all")
    private String therapyType;
}
