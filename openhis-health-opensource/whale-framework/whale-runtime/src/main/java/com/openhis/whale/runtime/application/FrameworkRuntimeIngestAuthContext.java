/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.runtime.application;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

final class FrameworkRuntimeIngestAuthContext {

    private static final String TOKEN_HEADER = "X-Framework-Runtime-Token";

    private FrameworkRuntimeIngestAuthContext() {
    }

    static String currentToken() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            HttpServletRequest request = attributes.getRequest();
            return request.getHeader(TOKEN_HEADER);
        } catch (Exception e) {
            return null;
        }
    }
}
