/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.id;

import org.hibernate.annotations.IdGeneratorType;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Hibernate 6 全局雪花 ID 生成策略：发号逻辑由 {@link SnowflakeIdGenerator} 单例承担。
 */
@IdGeneratorType(SnowflakeIdentifierGenerator.class)
@Target({FIELD, METHOD})
@Retention(RUNTIME)
public @interface SnowflakeId {
}
