/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.diagnosis.bodystructure.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 身体部位树查询模型。
 */
@Getter
@Setter
public class BodyStructureTreeQueryModel {
    /** 主键。 */
    private Long id;
    /** 业务号。 */
    private String busNo;
    /** 名称。 */
    private String name;
    /** 状态枚举编码。 */
    private String statusEnum;
    /** 子节点列表（树形组装，非持久化列）。 */
    private List<BodyStructureTreeQueryModel> children = new ArrayList<>();
}
