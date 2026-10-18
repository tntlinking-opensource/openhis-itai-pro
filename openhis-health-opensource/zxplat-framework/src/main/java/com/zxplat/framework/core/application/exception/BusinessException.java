/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.application.exception;

import java.util.Collections;
import java.util.Map;

/**
 * 业务异常
 */
public class BusinessException extends RuntimeException {

    private final String errorCode;
    private final Map<String, Object> details;

    public BusinessException(String message) {
        super(message);
        this.errorCode = "BUSINESS_ERROR";
        this.details = null;
    }

    public BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.details = null;
    }

    public BusinessException(String errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "BUSINESS_ERROR";
        this.details = null;
    }

    public BusinessException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = null;
    }

    public String getErrorCode() {
        return errorCode;
    }

    /**
     * 可选业务详情（如跳转补码用的目录主键），无则空 Map。
     */
    public Map<String, Object> getDetails() {
        if (details == null || details.isEmpty()) {
            return Collections.emptyMap();
        }
        return details;
    }
}
