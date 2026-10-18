/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.catalog;

import java.util.Collections;
import java.util.List;

/**
 * 从医保国家目录镜像（1308 手术操作）读取行，供院内「手术与操作」目录导入。
 *
 * <p>吉林实现：{@code InsuranceSurgeryCatalogSourceExtensionImpl}（module-chs-jilin-bridge）。</p>
 */
public interface InsuranceSurgeryCatalogSourceExtension {

    /**
     * 扩展是否已装配且可用。
     *
     * @return false 时导入应提示先启用医保模块并下载 1308 目录
     */
    default boolean isEnabled() {
        return false;
    }

    /**
     * 列出当前租户下有效的手术操作目录行（按 hilist_code 去重）。
     *
     * @return 行列表；无数据时返回空列表
     */
    default List<InsuranceSurgeryCatalogRow> listActiveSurgeryRows() {
        return Collections.emptyList();
    }
}
