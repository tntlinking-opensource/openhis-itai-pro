/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.application;

/**
 * 身份域查询侧常量（IN 分批等）。
 *
 * <p>与 health {@code GeneralSharedConstants#ID_IN_QUERY_CHUNK_SIZE} 同口径；identity 不得依赖 health 常量包。</p>
 */
public interface IdentityQueryConstants {

    /** 主键 {@code IN (...)} 单批上限。 */
    int ID_IN_QUERY_CHUNK_SIZE = 500;
}
