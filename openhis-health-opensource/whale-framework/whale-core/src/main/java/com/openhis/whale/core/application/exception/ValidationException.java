/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.application.exception;

import java.util.Map;

/**
 * 验证异常
 */
public class ValidationException extends BusinessException {

    private final Map<String, String> errors;

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message);
        this.errors = null;
    }

    public ValidationException(String message, Map<String, String> errors) {
        super("VALIDATION_ERROR", message);
        this.errors = errors;
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
