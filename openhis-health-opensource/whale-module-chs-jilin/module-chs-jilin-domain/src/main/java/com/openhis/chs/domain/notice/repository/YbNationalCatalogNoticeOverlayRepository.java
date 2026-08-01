/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.notice.repository;

import com.openhis.chs.domain.notice.entity.YbNationalCatalogNoticeOverlay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 通知叠加仓储。
 */
public interface YbNationalCatalogNoticeOverlayRepository
    extends JpaRepository<YbNationalCatalogNoticeOverlay, Long>,
    JpaSpecificationExecutor<YbNationalCatalogNoticeOverlay> {

    /**
     * 按业务键取未删除叠加行。
     */
    Optional<YbNationalCatalogNoticeOverlay>
        findByTenantIdAndContractIdAndListTypeAndHilistCodeAndIsDeletedFalse(
            Long tenantId, Long contractId, String listType, String hilistCode);

    /**
     * 按租户 + 国家码列叠加（跨 list_type，供门禁兜底）。
     */
    List<YbNationalCatalogNoticeOverlay> findByTenantIdAndHilistCodeAndIsDeletedFalse(
        Long tenantId, String hilistCode);

    /**
     * 按租户 + 多编码批量列叠加（选码合成）。
     */
    List<YbNationalCatalogNoticeOverlay> findByTenantIdAndHilistCodeInAndIsDeletedFalse(
        Long tenantId, Collection<String> hilistCodes);

    /**
     * 按租户 + 契约 + 多国家码预加载叠加（分批应用）。
     */
    List<YbNationalCatalogNoticeOverlay>
        findByTenantIdAndContractIdAndHilistCodeInAndIsDeletedFalse(
            Long tenantId, Long contractId, Collection<String> hilistCodes);
}
