/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
