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

package com.openhis.health.application.basic.orderbinding.request;

import lombok.Data;

/**
 * 按绑定目标解析可选单位列表：目标为诊疗定义或设备定义之一。
 */
@Data
/**
 * UnitOptionsByTargetRequest。
 */
public class UnitOptionsByTargetRequest {

    /** 目标逻辑表名（与初始化 targetTableOptions 的 code 一致）。 */
    /**
     * targetTable。
     */
    private String targetTable;

    /** 目标主键字符串。 */
    /**
     * targetId。
     */
    private String targetId;
}
