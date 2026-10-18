/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.group.repository;

import com.zxplat.plugin.peis.domain.group.entity.PeisContractType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * 团检合同类型字典仓储（仅 peis_contract_type）。
 */
public interface PeisContractTypeRepository
    extends JpaRepository<PeisContractType, Long>, JpaSpecificationExecutor<PeisContractType> {

    /**
     * 按启用状态列出类型，按排序号升序。
     *
     * @param enabledFlag 是否启用
     * @return 类型列表
     */
    List<PeisContractType> findByEnabledFlagAndIsDeletedFalseOrderBySortOrderAscIdAsc(Boolean enabledFlag);

    /**
     * 全部未删类型（含停用），按排序号升序。
     *
     * @return 类型列表
     */
    List<PeisContractType> findByIsDeletedFalseOrderBySortOrderAscIdAsc();

    /**
     * 按编码加载未删类型。
     *
     * @param typeCode 类型编码
     * @return 类型实体
     */
    Optional<PeisContractType> findFirstByTypeCodeAndIsDeletedFalse(String typeCode);
}
