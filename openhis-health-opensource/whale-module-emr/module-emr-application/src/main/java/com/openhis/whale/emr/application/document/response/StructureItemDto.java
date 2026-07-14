/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.document.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class StructureItemDto {

    /** 名称 */
    private String name;
    /** 文书标题 */
    private String title;
    private String nodeType;   // region / section / dataElement
    /** 结构树深度 */
    private Integer depth;
    /** 结构项所在页码（分页文书） */
    private Integer pageNo;
    /** 子结构项 */
    private List<StructureItemDto> children;
}
