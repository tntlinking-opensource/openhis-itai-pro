/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.application.exception;

/**
 * 实体不存在异常
 */
public class EntityNotFoundException extends BusinessException {

    public EntityNotFoundException(String message) {
        super("ENTITY_NOT_FOUND", message);
    }

    public EntityNotFoundException(String entityName, Long id) {
        super("ENTITY_NOT_FOUND", String.format("实体 %s (ID: %d) 不存在", entityName, id));
    }
}
