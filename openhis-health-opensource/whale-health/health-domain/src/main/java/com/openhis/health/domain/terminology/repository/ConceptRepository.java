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

package com.openhis.health.domain.terminology.repository;

import com.openhis.health.domain.terminology.entity.Concept;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 编码概念Repository
 *
 */
@Repository
public interface ConceptRepository
    extends JpaRepository<Concept, Long>,
            JpaSpecificationExecutor<Concept> {

    /**
     * 根据编码系统和编码查找概念
     */
    Optional<Concept> findByCodeSystemIdAndCode(Long codeSystemId, String code);

    /**
     * 根据编码系统查找所有概念（按排序号升序）
     */
    List<Concept> findByCodeSystemIdOrderBySortOrderAsc(Long codeSystemId);

    /**
     * 根据编码系统查找所有启用的概念（按排序号升序）
     */
    List<Concept> findByCodeSystemIdAndIsActiveTrueOrderBySortOrderAsc(Long codeSystemId);

    Optional<Concept> findTop1ByCodeSystemIdOrderBySortOrderDesc(Long codeSystemId);

    /**
     * 根据编码系统查找所有概念（按排序号升序，包含已禁用的）
     */
    List<Concept> findByCodeSystemIdOrderBySortOrderAscCodeAsc(Long codeSystemId);

    /**
     * 根据父概念查找子概念列表
     */
    List<Concept> findByParentOrderBySortOrderAsc(Concept parent);

    /**
     * 查找根概念（没有父概念的概念）
     */
    List<Concept> findByCodeSystemIdAndParentIsNullOrderBySortOrderAsc(Long codeSystemId);

    /**
     * 检查编码是否存在
     */
    boolean existsByCodeSystemIdAndCode(Long codeSystemId, String code);

    /**
     * 根据编码系统统计概念数量
     */
    long countByCodeSystemId(Long codeSystemId);
}
