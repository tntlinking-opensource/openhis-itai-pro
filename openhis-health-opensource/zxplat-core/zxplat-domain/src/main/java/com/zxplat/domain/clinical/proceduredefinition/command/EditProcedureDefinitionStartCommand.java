/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.proceduredefinition.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class EditProcedureDefinitionStartCommand {
    private List<Long> ids;
}
