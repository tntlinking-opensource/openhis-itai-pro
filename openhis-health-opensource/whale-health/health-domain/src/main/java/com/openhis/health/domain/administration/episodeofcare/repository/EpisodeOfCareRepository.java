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
