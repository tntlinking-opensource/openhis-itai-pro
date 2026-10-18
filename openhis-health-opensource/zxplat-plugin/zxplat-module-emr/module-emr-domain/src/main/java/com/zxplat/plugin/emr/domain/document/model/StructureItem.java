/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.domain.document.model;

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
