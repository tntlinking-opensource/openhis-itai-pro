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
}
