/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.catalog;

import java.util.Collections;
import java.util.List;

/**
 * 从医保国家目录镜像读取疾病/诊断类行，供院内诊断目录导入。
 *
 * <p>list_type 示例：1307 疾病与诊断、1309 门诊慢特病、1314 中医疾病、1315 中医证候。</p>
 *
 * <p>吉林实现：{@code InsuranceDiseaseCatalogSourceExtensionImpl}（module-chs-jilin-bridge）。</p>
 */
public interface InsuranceDiseaseCatalogSourceExtension {

    /**
     * 扩展是否已装配且可用。
     *
     * @return false 时导入应提示先启用医保模块并下载对应目录
     */
    default boolean isEnabled() {
        return false;
    }

    /**
     * 列出当前租户下指定 list_type 的有效目录行（按 hilist_code 去重）。
     *
     * @param listType 医保目录类型（如 1307）
     * @return 行列表；无数据或未启用时返回空列表
     */
    default List<InsuranceDiseaseCatalogRow> listActiveRows(String listType) {
        return Collections.emptyList();
    }
}
