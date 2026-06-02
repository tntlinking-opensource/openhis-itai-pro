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

package com.openhis.health.application.emergency.emgtriage.station.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 知识库维护列表/编辑行（DM-EMG-002）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "急诊知识库维护行")
public class EmgKnowledgeBaseItemMaintenanceDto {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String name;

    private String pinyinCode;

    private Integer triageLevel;

    private String type;

    private String category;

    private Integer sortOrder;

    /** 0 禁用 / 1 启用 */
    private Integer status;
}
