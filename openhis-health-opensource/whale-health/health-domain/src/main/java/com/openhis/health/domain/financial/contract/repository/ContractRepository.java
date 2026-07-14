/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.contract.repository;

import com.openhis.health.domain.financial.contract.entity.Contract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 费用性质仓储。
 */
public interface ContractRepository extends JpaRepository<Contract, Long> {
    /**
     * 按主键查询。
     * @param id 条件
     * @return 查询结果
     */
    Optional<Contract> findById(Long id);

    /**
     * 按业务号查询。
     * @param busNo 条件
     * @return 查询结果
     */
    Optional<Contract> findByBusNo(String busNo);

    /**
     * 按业务号批量查询费用性质。
     *
     * @param busNos 业务号集合
     * @return 合同列表
     */
    List<Contract> findAllByBusNoIn(Collection<String> busNos);

    /**
     * 按业务号查询。
     * @param busNo 条件
     * @param id 条件
     * @return 查询结果
     */
    Optional<Contract> findByBusNoAndIdNot(String busNo, Long id);

    /**
     * 按条件查询列表。
     * @param orgId 条件
     * @param pageable 条件
     * @return 查询结果
     */
    Page<Contract> findAllByOrgIdOrderByDisplayOrderAsc(Long orgId, Pageable pageable);

    /**
     * 按机构列出全部费用性质（主数据规模受控；排序与分页列表接口一致并增加主键稳定序）。
     *
     * @param orgId 机构标识
     * @return 合同实体列表，可能为空
     */
    List<Contract> findAllByOrgIdOrderByDisplayOrderAscIdAsc(Long orgId);
}
