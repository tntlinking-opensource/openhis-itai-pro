/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.platformdict.repository;

import com.openhis.chs.domain.platformdict.entity.YbPlatformDictItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

/** 1901 平台字典镜像仓储（条件下推 DB，禁 {@code @Query}） */
public interface YbPlatformDictItemRepository
        extends JpaRepository<YbPlatformDictItem, Long>, JpaSpecificationExecutor<YbPlatformDictItem> {

    List<YbPlatformDictItem> findByTenantIdAndDictTypeAndDictValueInAndIsDeletedFalse(
        Long tenantId, String dictType, Collection<String> dictValues);

    Page<YbPlatformDictItem> findByTenantIdAndDictTypeAndIsDeletedFalse(
        Long tenantId, String dictType, Pageable pageable);
}
