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

package com.openhis.health.domain.custom.terminologycodemapping.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 术语映射查询模型。
 */
@Getter
@Setter
public class TerminologyMappingQueryModel {
    /**
     * 映射记录标识。
     */
    private Long id;
    /**
     * 源术语所在表名。
     */
    private String sourceItemTable;
    /**
     * 源术语编码/编号。
     */
    private String sourceItemNo;
    /**
     * 目标术语编码/编号。
     */
    private String targetItemNo;
    /**
     * 目标术语名称（可空）。
     */
    private String targetName;
    /**
     * 是否启用（true=有效映射）。
     */
    private Boolean activeFlag;
}
