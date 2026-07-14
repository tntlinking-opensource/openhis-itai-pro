/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.platformdict.repository;

import com.openhis.chs.domain.platformdict.entity.YbPlatformDictSyncLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** {@code yb_platform_dict_sync_log} JPA 仓储 */
public interface YbPlatformDictSyncLogRepository extends JpaRepository<YbPlatformDictSyncLog, Long> {

    Optional<YbPlatformDictSyncLog> findFirstByTenantIdAndDictTypeAndIsDeletedFalseOrderByStartedAtDesc(
        Long tenantId, String dictType);
}
