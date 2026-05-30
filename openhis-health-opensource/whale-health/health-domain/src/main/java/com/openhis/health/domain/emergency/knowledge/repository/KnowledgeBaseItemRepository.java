/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.emergency.knowledge.repository;

import com.openhis.health.domain.emergency.knowledge.entity.KnowledgeBaseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * 急诊知识库条目仓储（SDD §4.5）。
 */
public interface KnowledgeBaseItemRepository
        extends JpaRepository<KnowledgeBaseItem, Long>, JpaSpecificationExecutor<KnowledgeBaseItem> {

    Optional<KnowledgeBaseItem> findByIdAndIsDeletedFalse(Long id);

    boolean existsByTypeAndNameIgnoreCaseAndIsDeletedFalse(String type, String name);

    boolean existsByTypeAndNameIgnoreCaseAndIdNotAndIsDeletedFalse(String type, String name, Long id);

    Optional<KnowledgeBaseItem> findByTypeAndSortOrderAndIsDeletedFalse(String type, Integer sortOrder);

    Optional<KnowledgeBaseItem> findByTypeAndSortOrderAndIdNotAndIsDeletedFalse(String type, Integer sortOrder, Long id);

    List<KnowledgeBaseItem> findByTypeAndStatusAndIsDeletedFalseOrderBySortOrderAsc(String type, Integer status);
}
