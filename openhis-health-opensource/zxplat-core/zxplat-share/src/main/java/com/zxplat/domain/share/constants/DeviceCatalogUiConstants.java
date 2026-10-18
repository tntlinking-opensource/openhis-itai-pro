/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * DeviceCatalogUiConstants。
 */
public interface DeviceCatalogUiConstants {

    /**
     * 设备定义更新命令中的默认可编辑性编码：放行后续保存链路。
     *
     * @deprecated 请使用 {@link CatalogEditabilityConstants#OK}；保留别名兼容旧引用。
     */
    @Deprecated
/** DeviceCatalogUiConstants：EDITABILITY_OK，值 {@code CatalogEditabilityConstants.OK}。 */
    String EDITABILITY_OK = CatalogEditabilityConstants.OK;
}
