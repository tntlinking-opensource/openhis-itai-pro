/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.module;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 扩展 Module 的 Flyway 迁移定义（来自 {@code META-INF/openhis/flyway-module.json}）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenHisFlywayModuleDefinition {

    private String moduleId;
    private String enabledProperty;
    private String locations;
    private String historyTable;

    public String moduleId() {
        return moduleId;
    }

    public void setModuleId(String moduleId) {
        this.moduleId = moduleId;
    }

    public String enabledProperty() {
        return enabledProperty;
    }

    public void setEnabledProperty(String enabledProperty) {
        this.enabledProperty = enabledProperty;
    }

    public String locations() {
        return locations;
    }

    public void setLocations(String locations) {
        this.locations = locations;
    }

    public String historyTable() {
        return historyTable;
    }

    public void setHistoryTable(String historyTable) {
        this.historyTable = historyTable;
    }
}
