/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.proceduredefinition.queries;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 手术操作定义维护分页入参。
 */
@Getter
@Setter
public class ProcedureDefinitionPageQuery extends AbstractPageQuery {
    private static final long serialVersionUID = 1L;

    /** 目录来源筛选码；空表示不按来源收窄。 */
    private String sourceEnum;
}
