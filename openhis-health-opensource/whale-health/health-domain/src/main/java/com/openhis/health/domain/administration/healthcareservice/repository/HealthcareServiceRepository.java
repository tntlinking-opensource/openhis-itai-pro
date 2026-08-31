/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.healthcareservice.repository;

import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 医疗服务聚合根仓储。
 * <p>
 * 持久化与查询「医疗服务」主数据，支撑类型、状态筛选及按主键加载。
 */
public interface HealthcareServiceRepository extends JpaRepository<HealthcareService, Long>, JpaSpecificationExecutor<HealthcareService> {
    /** 按主键查询医疗服务。 */
    Optional<HealthcareService> findById(Long id);

    /** 按状态枚举查询全部医疗服务（如草稿、已发布、退役等）。 */
    List<HealthcareService> findAllByStatusEnum(String statusEnum);

    /** 按多个状态枚举查询医疗服务（挂号类型维护台等需同时展示启用与停用）。 */
    List<HealthcareService> findAllByStatusEnumIn(Collection<String> statusEnums);

    /**
     * 按业务编号与状态查询医疗服务（产品级号源识别，如历史号复诊）。
     *
     * @param busNo 业务编号
     * @param statusEnum 状态码
     * @return 命中实体；不存在时 empty
     */
    Optional<HealthcareService> findByBusNoAndStatusEnum(String busNo, String statusEnum);
}
