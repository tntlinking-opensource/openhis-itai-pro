/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.diagnosis.bodystructure.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 身体部位详情查询模型。
 */
@Getter
@Setter
public class BodyStructureInfoQueryModel {
    /** 主键。 */
    private Long id;
    /** 业务号。 */
    private String busNo;
    /** 名称。 */
    private String name;
    /** 状态枚举编码。 */
    private String statusEnum;
    /** 上级部位主键；根节点为 null。 */
    private Long parentId;
    /** pystr。 */
    private String pyStr;
    /** wbstr。 */
    private String wbStr;
}
