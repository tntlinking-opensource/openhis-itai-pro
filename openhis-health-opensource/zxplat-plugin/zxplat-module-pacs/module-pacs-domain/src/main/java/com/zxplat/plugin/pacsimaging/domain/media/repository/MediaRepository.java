/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.domain.media.repository;

import com.zxplat.plugin.pacsimaging.domain.media.entity.Media;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Media 聚合根仓储。
 */
public interface MediaRepository extends JpaRepository<Media, Long> {

    Optional<Media> findByIdAndWorkItemId(Long id, Long workItemId);

    Optional<Media> findByObjectKeyAndTenantId(String objectKey, Long tenantId);

    List<Media> findByWorkItemIdOrderByCreatedAtDesc(Long workItemId);

    long countByWorkItemId(Long workItemId);
}
