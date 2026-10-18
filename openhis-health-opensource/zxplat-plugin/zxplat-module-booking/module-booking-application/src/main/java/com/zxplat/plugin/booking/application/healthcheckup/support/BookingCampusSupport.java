/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.support;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/**
 * 校区模块运行时开关（方案 A：classpath 有 campus 则默认开，显式 {@code enabled=false} 可关）。
 */
@Component
@RequiredArgsConstructor
public class BookingCampusSupport {

    private static final String CAMPUS_ENABLED_PROPERTY = "zxplat.modules.campus.enabled";

    private static final String CAMPUS_AUTO_CONFIGURATION =
            "com.zxplat.plugin.campus.application.config.CampusAutoConfiguration";

    private final Environment environment;

    /**
     * 校区模块是否启用。
     *
     * @return 启用时 true
     */
    public boolean isCampusModuleEnabled() {
        boolean onClasspath = ClassUtils.isPresent(CAMPUS_AUTO_CONFIGURATION, this.getClass().getClassLoader());
        if (!onClasspath) {
            return false;
        }
        Boolean configured = this.environment.getProperty(CAMPUS_ENABLED_PROPERTY, Boolean.class);
        if (configured == null) {
            return true;
        }
        return configured.booleanValue();
    }
}
