/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.proceduredefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProcedureDefinitionSearchItemQueryModel {
    private Long id;
    private String procedureCode;
    private String name;
    private String pyStr;
    private String wbStr;
    private String statusEnum;
}
