/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.platformdict.repository;

import com.openhis.chs.domain.platformdict.entity.YbPlatformDictItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface YbPlatformDictItemRepository extends JpaRepository<YbPlatformDictItem, Long> {

    List<YbPlatformDictItem> findByTenantIdAndDictTypeAndDictValueInAndIsDeletedFalse(
        Long tenantId, String dictType, Collection<String> dictValues);

    Page<YbPlatformDictItem> findByTenantIdAndDictTypeAndIsDeletedFalse(
        Long tenantId, String dictType, Pageable pageable);

    @Query("""
        SELECT e FROM YbPlatformDictItem e
        WHERE e.tenantId = :tenantId
          AND e.dictType = :dictType
          AND e.isDeleted = false
          AND (e.dictValue LIKE :keyword OR e.dictLabel LIKE :keyword)
        """)
    Page<YbPlatformDictItem> findByTenantIdAndDictTypeAndKeywordAndIsDeletedFalse(
        @Param("tenantId") Long tenantId,
        @Param("dictType") String dictType,
        @Param("keyword") String keyword,
        Pageable pageable);
}
