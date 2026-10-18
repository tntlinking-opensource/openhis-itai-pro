/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.infrastructure.repository;

import com.zxplat.framework.identity.domain.OrganizationUnit;
import com.zxplat.framework.core.infrastructure.transaction.SkipSoftDeleteFilter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 组织单元 Repository（仅派生查询，禁止 {@code @Query}）。
 */
@Repository
public interface OrganizationUnitRepository extends JpaRepository<OrganizationUnit, Long>,
    JpaSpecificationExecutor<OrganizationUnit> {

    Optional<OrganizationUnit> findByCode(String code);

    /**
     * 按 code 查 OU（含已软删），供 HIS 同步恢复逻辑删除行，避免同 code 唯一约束冲突。
     *
     * @param code 组织单元编码
     * @return 命中行（含 is_deleted=true）
     */
    @SkipSoftDeleteFilter
    Optional<OrganizationUnit> findFirstByCode(String code);

    /**
     * 按 code 批量加载组织单元（登录科室同步避免逐条 findByCode）。
     *
     * @param codes OU code 集合；调用方须保证非空
     * @return 命中的组织单元列表（可能少于入参）
     */
    List<OrganizationUnit> findByCodeIn(Collection<String> codes);

    List<OrganizationUnit> findByParentIdOrderBySortOrderAsc(Long parentId);

    List<OrganizationUnit> findByParentIdIsNullOrderBySortOrderAsc();

    boolean existsByCode(String code);
}
