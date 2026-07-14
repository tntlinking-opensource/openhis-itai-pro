/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
