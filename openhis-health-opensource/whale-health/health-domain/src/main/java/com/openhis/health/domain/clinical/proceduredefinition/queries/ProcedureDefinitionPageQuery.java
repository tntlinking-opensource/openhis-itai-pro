/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.proceduredefinition.queries;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProcedureDefinitionPageQuery extends AbstractPageQuery {
    private static final long serialVersionUID = 1L;
    private String sourceEnum;
}
