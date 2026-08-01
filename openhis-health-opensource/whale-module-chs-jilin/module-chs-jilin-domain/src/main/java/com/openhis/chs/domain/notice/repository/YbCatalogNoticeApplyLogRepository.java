/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.notice.repository;

import com.openhis.chs.domain.notice.entity.YbCatalogNoticeApplyLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 应用审计仓储（M1 占位）。
 */
public interface YbCatalogNoticeApplyLogRepository extends JpaRepository<YbCatalogNoticeApplyLog, Long> {
}
