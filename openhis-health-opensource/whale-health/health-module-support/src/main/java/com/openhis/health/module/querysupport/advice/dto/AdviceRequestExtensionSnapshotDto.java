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

package com.openhis.health.module.querysupport.advice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 医嘱/执行单请求扩展快照（由库表 content_json 解析，B.6.2 禁止对外下发原始 JSON 串）。
 */
@Getter
@Setter
@Schema(description = "医嘱请求扩展快照")
public class AdviceRequestExtensionSnapshotDto {

    /** 根为 JSON 对象时的键值对（如停嘱时间等）。 */
    @Schema(description = "对象形态扩展字段")
    private Map<String, Object> attributes = new LinkedHashMap<>();

    /** 根为 JSON 数组时的条目列表（如中药多味配伍）。 */
    @Schema(description = "数组形态扩展条目")
    private List<Map<String, Object>> entries = new ArrayList<>();
}
