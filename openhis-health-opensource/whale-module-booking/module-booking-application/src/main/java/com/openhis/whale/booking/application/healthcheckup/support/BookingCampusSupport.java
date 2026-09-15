/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.support;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 校区模块运行时开关（读取 {@code openhis.modules.campus.enabled}）。
 */
@Component
@RequiredArgsConstructor
public class BookingCampusSupport {

    private static final String CAMPUS_ENABLED_PROPERTY = "openhis.modules.campus.enabled";

    private final Environment environment;

    /**
     * 校区模块是否启用。
     *
     * @return 启用时 true
     */
    public boolean isCampusModuleEnabled() {
        return "true".equals(this.environment.getProperty(CAMPUS_ENABLED_PROPERTY));
    }
}
