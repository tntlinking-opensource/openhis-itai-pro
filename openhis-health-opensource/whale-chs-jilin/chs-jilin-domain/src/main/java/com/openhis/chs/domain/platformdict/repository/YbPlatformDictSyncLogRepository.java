/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.platformdict.repository;

import com.openhis.chs.domain.platformdict.entity.YbPlatformDictSyncLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface YbPlatformDictSyncLogRepository extends JpaRepository<YbPlatformDictSyncLog, Long> {

    Optional<YbPlatformDictSyncLog> findFirstByTenantIdAndDictTypeAndIsDeletedFalseOrderByStartedAtDesc(
        Long tenantId, String dictType);
}
