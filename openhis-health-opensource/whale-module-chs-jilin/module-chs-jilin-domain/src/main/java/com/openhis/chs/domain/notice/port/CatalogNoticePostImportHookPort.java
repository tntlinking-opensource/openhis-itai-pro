/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.notice.port;

/**
 * CAT-002 目录入库成功后通知侧钩子（domain 端口；application 实现）。
 * <p>失败不得抛出影响下载成功。</p>
 */
public interface CatalogNoticePostImportHookPort {

    /**
     * 目录镜像入库成功回调。
     *
     * @param tenantId   租户
     * @param contractId 契约
     * @param listType   目录类别
     * @param operatorId 操作人（可空）
     */
    void onCatalogImportSuccess(Long tenantId, Long contractId, String listType, Long operatorId);
}
