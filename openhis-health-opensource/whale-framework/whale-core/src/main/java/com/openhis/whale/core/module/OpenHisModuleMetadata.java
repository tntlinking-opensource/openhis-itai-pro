/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.module;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 扩展 Module 的 AppService 路由元数据（来自 {@code META-INF/openhis/module-metadata.json}）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenHisModuleMetadata {

    private String moduleId;
    private String displayName;
    private String enabledProperty;
    private String autoConfigurationClass;
    private Set<String> appServiceNames = new LinkedHashSet<>();

    public String moduleId() {
        return moduleId;
    }

    public void setModuleId(String moduleId) {
        this.moduleId = moduleId;
    }

    public String displayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String enabledProperty() {
        return enabledProperty;
    }

    public void setEnabledProperty(String enabledProperty) {
        this.enabledProperty = enabledProperty;
    }

    public String autoConfigurationClass() {
        return autoConfigurationClass;
    }

    public void setAutoConfigurationClass(String autoConfigurationClass) {
        this.autoConfigurationClass = autoConfigurationClass;
    }

    public Set<String> appServiceNames() {
        return appServiceNames == null ? Set.of() : Collections.unmodifiableSet(appServiceNames);
    }

    public void setAppServiceNames(Set<String> appServiceNames) {
        this.appServiceNames = appServiceNames;
    }
}
