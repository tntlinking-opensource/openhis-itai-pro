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

package com.openhis.health.domain.medication.medicationdispense.repository;

import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.share.enums.DispenseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 药品发放仓储。
 */
public interface MedicationDispenseRepository extends JpaRepository<MedicationDispense, Long>, JpaSpecificationExecutor<MedicationDispense> {

    /**
     * 按主键查询未删除发放记录。
     *
     * @param id 主键
     * @return 发放记录
     */
    Optional<MedicationDispense> findById(Long id);

    /**
     * 按就诊查询未删除发放记录。
     *
     * @param encounterId 就诊标识
     * @return 发放记录集合
     */
    List<MedicationDispense> findByEncounterId(Long encounterId);

    /**
     * 按就诊主键集合批量查询未删除发放记录（退药队列聚合等读场景，避免按就诊循环查库）。
     *
     * @param encounterIds 就诊主键集合
     * @return 发放记录集合
     */
    List<MedicationDispense> findByEncounterIdIn(Collection<Long> encounterIds);

    /**
     * 按汇总号查询未删除发放记录。
     *
     * @param summaryNo 汇总号
     * @return 发放记录集合
     */
    List<MedicationDispense> findBySummaryNo(String summaryNo);

    /**
     * 按医嘱集合查询未删除发放记录。
     *
     * @param medReqIds 医嘱标识集合
     * @return 发放记录集合
     */
    List<MedicationDispense> findByMedReqIdIn(Collection<Long> medReqIds);

    /**
     * 按主键集合查询未删除发放记录。
     *
     * @param ids 主键集合
     * @return 发放记录集合
     */
    List<MedicationDispense> findByIdIn(Collection<Long> ids);

    /**
     * 按医嘱和状态集合查询未删除发放记录。
     *
     * @param medReqId 医嘱标识
     * @param statuses 状态集合
     * @return 是否存在
     */
    boolean existsByMedReqIdAndStatusEnumIn(Long medReqId, Collection<DispenseStatus> statuses);

    /**
     * 按就诊与状态查询未删除发放记录。
     *
     * @param encounterId 就诊标识
     * @param statusEnum 状态编码
     * @return 发放记录集合
     */
    List<MedicationDispense> findByEncounterIdAndStatusEnum(Long encounterId, DispenseStatus statusEnum);

    /**
     * 判断药品是否存在有效发放引用。
     *
     * @param medicationId 药品标识
     * @return 是否存在引用
     */
    boolean existsByMedicationId(Long medicationId);

    /**
     * 按执行记录主键集合批量查询未删除发放记录（取消执行 Dispense 分轨定位，DM-MED-002 §6.2.14）。
     *
     * @param procedureIds 执行记录主键集合
     * @return 发放记录集合；{@code procedureIds} 为空时返回空列表
     */
    List<MedicationDispense> findByProcedureIdIn(Collection<Long> procedureIds);

    /**
     * 汇总领药：仅当源态为 {@code PREPARATION} 且无汇总号时批量写入汇总号并迁移至 {@code SUMMARIZED}（GAP-INV-003 / GAP-STALE-006）。
     *
     * @return 实际更新行数
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE MedicationDispense d
           SET d.summaryNo = :summaryNo,
               d.statusEnum = :targetStatus,
               d.statusChangedTime = :statusChangedTime
         WHERE d.id IN :ids
           AND d.statusEnum = :sourceStatus
           AND (d.summaryNo IS NULL OR d.summaryNo = '')
           AND d.isDeleted = false
        """)
    int summarizeIfPreparationWhereIdIn(
        @Param("ids") Collection<Long> ids,
        @Param("summaryNo") String summaryNo,
        @Param("targetStatus") DispenseStatus targetStatus,
        @Param("sourceStatus") DispenseStatus sourceStatus,
        @Param("statusChangedTime") LocalDateTime statusChangedTime);

    /**
     * 汇总核发：仅当源态为 {@code SUMMARIZED} 时批量完成发放（GAP-INV-004 / GAP-STALE-002）。
     *
     * @return 实际更新行数
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE MedicationDispense d
           SET d.statusEnum = :targetStatus,
               d.practitionerId = :practitionerId,
               d.dispenseQuantity = d.quantity,
               d.dispenseTime = :dispenseTime,
               d.statusChangedTime = :dispenseTime
         WHERE d.summaryNo = :summaryNo
           AND d.statusEnum = :sourceStatus
           AND d.isDeleted = false
        """)
    int completeSummaryDispenseWhereSummaryNoAndSourceStatus(
        @Param("summaryNo") String summaryNo,
        @Param("targetStatus") DispenseStatus targetStatus,
        @Param("sourceStatus") DispenseStatus sourceStatus,
        @Param("practitionerId") Long practitionerId,
        @Param("dispenseTime") LocalDateTime dispenseTime);
}

