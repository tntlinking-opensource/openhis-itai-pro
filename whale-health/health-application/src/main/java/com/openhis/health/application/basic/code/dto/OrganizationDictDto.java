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

package com.openhis.health.application.basic.code.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通用「字典行」结构（编码体系枚举目录等）：value/label 与可选树形 children。
 *
 * <p>注意：业务下拉优先复用 {@link com.openhis.whale.core.application.dto.OptionDto}；本类型保留给历史接口形状对齐。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationDictDto {

    /** 选项值（常为编码或枚举名）。 */
    private String value;
    /** 展示标签。 */
    private String label;
    /** 子节点（树形字典时使用）。 */
    private List<OrganizationDictDto> children;

    /**
     * 构造无子节点的字典选项行。
     *
     * @param value 选项值
     * @param label 展示标签
     */
    public OrganizationDictDto(String value, String label) {
        this.value = value;
        this.label = label;
    }
}
