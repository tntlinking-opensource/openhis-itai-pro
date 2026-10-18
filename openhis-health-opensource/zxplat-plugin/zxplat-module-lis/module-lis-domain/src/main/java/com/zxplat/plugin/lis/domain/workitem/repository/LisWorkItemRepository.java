/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.lis.domain.workitem.repository;

import com.zxplat.plugin.lis.domain.workitem.entity.LisWorkItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LisWorkItemRepository extends JpaRepository<LisWorkItem, Long> {
}