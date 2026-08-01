/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.proceduredefinition.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 医保「手术与操作」目录导入行。
 */
@Getter
@Setter
public class GeneralOpImportRowCommand {
    private String procedureCode;
    private String name;
    private String pyStr;
}
