/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.activitydefinition.queries;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 诊疗项目目录分页查询入参。
 *
 * <p>分页与关键字见 {@link AbstractPageQuery}；Spring Data 仍可另行传入 {@code Pageable}，二者并存时以实现类约定为准。</p>
 */
@Getter
@Setter
public class ActivityDefinitionPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 目录类别编码（可选）。 */
    private String categoryCode;

    /** 发布状态（可选）。 */
    private String statusEnum;

    /** 诊疗类型（可选）。 */
    private String typeEnum;

    /**
     * 是否套餐/含子项（可选）。
     * <p>体检目录：单项项目传 {@code false}，套餐传 {@code true}。</p>
     */
    private Boolean childrenFlag;

    /**
     * 是否包含「已被套餐引用为子项」的目录行。
     *
     * <p>默认 {@code null}/{@code false}：临床/组套/报表等不暴露明细子项，仅展示可单独开立的父项。
     * 仅<strong>诊疗目录维护</strong>、<strong>体检目录配置</strong>、<strong>目录导出</strong>等应传 {@code true}；
     * 医保对码走独立查询，不经本字段。</p>
     */
    private Boolean includeReferencedPackageChildren;
}
