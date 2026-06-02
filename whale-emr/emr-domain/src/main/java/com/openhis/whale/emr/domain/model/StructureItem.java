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

package com.openhis.whale.emr.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 文书结构项（与 Tiptap 节点对应）。用于 parseStructure 返回，应用层可映射为 StructureItemDto。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StructureItem {

    private String name;
    private String title;
    private String nodeType;   // region / section / dataElement
    private Integer depth;
    private Integer pageNo;
    private List<StructureItem> children;
}
