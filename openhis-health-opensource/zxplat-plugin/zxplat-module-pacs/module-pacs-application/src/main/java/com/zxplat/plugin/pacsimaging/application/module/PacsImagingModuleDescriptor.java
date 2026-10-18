/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.application.module;

import com.zxplat.framework.core.module.ZxplatModuleDescriptor;
import org.springframework.stereotype.Component;

/**
 * PACS 影像扩展模块描述（启动日志 / 运维可观测）。
 */
@Component
public class PacsImagingModuleDescriptor implements ZxplatModuleDescriptor {

    @Override
    public String moduleId() {
        return "pacs-imaging";
    }

    @Override
    public String displayName() {
        return "PACS 影像扩展";
    }

    @Override
    public String enabledProperty() {
        return "zxplat.modules.pacs.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[0];
    }

    @Override
    public String[] featureKeys() {
        return new String[] {
            "pacsViewerUrlConfig",
            "pacsImageCapture",
            "pacsViewer"
        };
    }
}
