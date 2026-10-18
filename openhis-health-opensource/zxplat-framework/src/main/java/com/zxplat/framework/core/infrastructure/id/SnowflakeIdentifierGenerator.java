/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.id;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

import java.io.Serializable;

/**
 * Hibernate {@link IdentifierGenerator} 适配器：委托 {@link SnowflakeIdGenerator#nextId()}。
 * <p>每个 {@link SnowflakeId} 字段会实例化本类，须保持无状态、构造轻量。</p>
 */
public class SnowflakeIdentifierGenerator implements IdentifierGenerator {

    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object object) {
        return SnowflakeIdGenerator.nextId();
    }
}
