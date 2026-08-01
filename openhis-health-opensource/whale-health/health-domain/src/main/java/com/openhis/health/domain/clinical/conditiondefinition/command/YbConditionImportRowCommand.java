/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.conditiondefinition.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 医保诊断目录导入行（写入指定 sourceEnum）。
 */
@Getter
@Setter
public class YbConditionImportRowCommand {
    private String conditionCode;
    private String name;
    private String pyStr;
}
