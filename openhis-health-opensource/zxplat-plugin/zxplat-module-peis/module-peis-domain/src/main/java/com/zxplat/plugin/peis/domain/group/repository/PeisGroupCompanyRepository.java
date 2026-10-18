/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.group.repository;

import com.zxplat.plugin.peis.domain.group.entity.PeisGroupCompany;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface PeisGroupCompanyRepository
    extends JpaRepository<PeisGroupCompany, Long>, JpaSpecificationExecutor<PeisGroupCompany> {

    boolean existsByParentIdAndIsDeletedFalse(Long parentId);

    List<PeisGroupCompany> findByParentIdAndIsDeletedFalse(Long parentId);

    List<PeisGroupCompany> findByParentIdIsNullAndIsDeletedFalse();
}
