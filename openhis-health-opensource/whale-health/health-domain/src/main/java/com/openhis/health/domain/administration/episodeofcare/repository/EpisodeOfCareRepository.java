/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.episodeofcare.repository;

import com.openhis.health.domain.administration.episodeofcare.entity.EpisodeOfCare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * 疗程聚合仓储：按单号、患者与机构查询疗程，支撑列表与详情。
 */
public interface EpisodeOfCareRepository extends JpaRepository<EpisodeOfCare, Long>, JpaSpecificationExecutor<EpisodeOfCare> {
    /**
     * 按主键加载疗程。
     */
    Optional<EpisodeOfCare> findById(Long id);

    /**
     * 按业务单号唯一定位。
     */
    Optional<EpisodeOfCare> findFirstByBusNo(String busNo);

    /**
     * 患者名下全部疗程。
     */
    List<EpisodeOfCare> findAllByPatientId(Long patientId);

    /**
     * 机构下全部疗程（注意数据量，宜配合分页规格）。
     */
    List<EpisodeOfCare> findAllByOrganizationId(Long organizationId);

    /**
     * 按患者与状态过滤疗程列表。
     */
    List<EpisodeOfCare> findAllByPatientIdAndStatusEnum(Long patientId, String statusEnum);
}
