/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.application.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一下拉选项 DTO（供前端下拉/树形选择使用）
 * 平铺列表：仅使用 sort、value、label；树形列表：可设置 busNo、children；
 * 需要前端拼音/五笔模糊过滤时可选填充 pyStr、wbStr。
 *
 * @author Whale Health
 */
@Data
@NoArgsConstructor
public class OptionDto {

    /** 排序 */
    private Long sort;
    /** 选项值（前端绑定 value，统一为 String） */
    private String value;
    /** 显示文案（前端绑定 label） */
    private String label;
    /** 业务编号（树形或需要层级编码时使用，可选） */
    private String busNo;
    /** 子节点（树形下拉时使用，平铺时为 null 或空） */
    private List<OptionDto> children;
    /** 拼音检索码（可选；申请单部位/样本等 filterable 下拉） */
    private String pyStr;
    /** 五笔检索码（可选；申请单部位/样本等 filterable 下拉） */
    private String wbStr;

    /**
     * 平铺选项：sort + value + label。
     *
     * @param sort  排序
     * @param value 选项值
     * @param label 显示文案
     */
    public OptionDto(Long sort, String value, String label) {
        this.sort = sort;
        this.value = value;
        this.label = label;
        this.busNo = null;
        this.children = null;
        this.pyStr = null;
        this.wbStr = null;
    }

    /**
     * 树形选项：sort + value + label + busNo + children。
     *
     * @param sort     排序
     * @param value    选项值
     * @param label    显示文案
     * @param busNo    业务编号
     * @param children 子节点
     */
    public OptionDto(Long sort, String value, String label, String busNo, List<OptionDto> children) {
        this.sort = sort;
        this.value = value;
        this.label = label;
        this.busNo = busNo;
        this.children = children;
        this.pyStr = null;
        this.wbStr = null;
    }
}
