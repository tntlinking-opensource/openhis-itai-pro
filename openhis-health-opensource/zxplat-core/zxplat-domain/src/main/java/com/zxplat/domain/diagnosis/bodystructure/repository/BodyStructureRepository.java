/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.diagnosis.bodystructure.repository;

import com.zxplat.domain.diagnosis.bodystructure.entity.BodyStructure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 身体部位仓储。
 */
public interface BodyStructureRepository extends JpaRepository<BodyStructure, Long>, JpaSpecificationExecutor<BodyStructure> {

    /**
     * 按主键查询。
     * @param id 条件
     * @return 查询结果
     */
    Optional<BodyStructure> findById(Long id);

    /**
     * 按条件查询。
     * @param ids 条件
     * @return 查询结果
     */
    List<BodyStructure> findByIdIn(Collection<Long> ids);

    /**
     * 按业务号查询。
     * @param busNo 条件
     * @return 查询结果
     */
    boolean existsByBusNo(String busNo);

    /**
     * 按业务号查询。
     * @param busNo 条件
     * @param id 条件
     * @return 查询结果
     */
    boolean existsByBusNoAndIdNot(String busNo, Long id);

    /**
     * 按业务号查询。
     * @return 查询结果
     */
    List<BodyStructure> findAllByOrderByBusNoAsc();

}
