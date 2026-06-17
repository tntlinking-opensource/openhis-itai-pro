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

package com.openhis.whale.booking.domain.appointment.repository;

import com.openhis.whale.booking.domain.appointment.entity.Appointment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * 预约与候诊仓储。
 *
 * <p>约束：禁止 {@code @Query}，查询过滤条件必须下推到数据库侧完成。</p>
 */
public interface AppointmentRepository extends JpaRepository<Appointment, Long>, JpaSpecificationExecutor<Appointment> {

    /**
     * 按就诊标识加载候诊队列形态下的有效项。
     *
     * @param encounterId 就诊标识
     * @return 有效项
     */
    Optional<Appointment> findByEncounterId(Long encounterId);

    /**
     * 按机构与诊室分页查询有效候诊队列项。
     *
     * @param organizationId 机构
     * @param destinationLocationId 诊室/地点
     * @param pageable 分页
     * @return 队列分页
     */
    Page<Appointment> findByOrganizationIdAndDestinationLocationId(Long organizationId, Long destinationLocationId, Pageable pageable);

    /**
     * 批量按就诊查询有效项。
     *
     * @param encounterIds 就诊标识集合
     * @return 有效项集合
     */
    List<Appointment> findByEncounterIdIn(List<Long> encounterIds);

    /**
     * 按患者查询有效预约/候诊项。
     *
     * @param patientId 患者标识
     * @return 有效项集合
     */
    List<Appointment> findByPatientId(Long patientId);

    /**
     * 按机构与诊室查询有效项（用于调序重排等场景）。
     *
     * <p>注意：该查询可能返回较大集合；调用方应限定在单诊室维度使用。</p>
     *
     * @param organizationId 机构
     * @param destinationLocationId 诊室/地点
     * @return 有效项集合
     */
    List<Appointment> findByOrganizationIdAndDestinationLocationId(Long organizationId, Long destinationLocationId);

    /**
     * 按号源批量查询有效门诊预约（停诊向导扫描）。
     *
     * @param slotIds 号源主键集合
     * @return 有效预约列表
     */
    List<Appointment> findBySlotIdInAndIsDeletedFalse(List<Long> slotIds);
}
