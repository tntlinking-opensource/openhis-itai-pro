/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationrequest.repository;

import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

/**
 * 药品申请仓储。
 */
public interface MedicationRequestRepository extends JpaRepository<MedicationRequest, Long>,
    JpaSpecificationExecutor<MedicationRequest> {
    /**
     * 按主键查询未删除药品申请。
     *
     * @param id 主键
     * @return 药品申请
     */
    Optional<MedicationRequest> findById(Long id);

    /**
     * 按就诊查询未删除药品申请。
     *
     * @param encounterId 就诊标识
     * @return 申请集合
     */
    List<MedicationRequest> findByEncounterId(Long encounterId);

    /**
     * 按就诊与生成来源过滤药品申请（门诊医生站医嘱列表 BR-ORD-010）。
     *
     * @param encounterId 就诊标识
     * @param generateSourceEnum 生成来源持久化码
     * @return 申请集合
     */
    List<MedicationRequest> findByEncounterIdAndGenerateSourceEnum(Long encounterId, String generateSourceEnum);

    /**
     * 按患者查询未删除药品申请。
     *
     * @param patientId 患者标识
     * @return 申请集合
     */
    List<MedicationRequest> findByPatientId(Long patientId);

    /**
     * 校验业务号是否已存在。
     *
     * @param busNo 业务号
     * @return 是否存在
     */
    boolean existsByBusNo(String busNo);

    /**
     * 按主键集合查询未删除药品申请。
     *
     * @param ids 主键集合
     * @return 药品申请集合
     */
    List<MedicationRequest> findByIdIn(Collection<Long> ids);

    /**
     * 按患者并排除当前就诊查询历史药品申请。
     *
     * @param patientId 患者标识
     * @param encounterId 当前就诊标识
     * @return 历史申请集合
     */
    List<MedicationRequest> findByPatientIdAndEncounterIdNot(Long patientId, Long encounterId);

    /**
     * 判断药品是否存在有效医嘱引用。
     *
     * @param medicationId 药品标识
     * @return 是否存在引用
     */
    boolean existsByMedicationId(Long medicationId);

    /**
     * 按原药品申请主键集合查询关联退药/负向申请行（refund_medicine_id 命中）。
     *
     * @param refundMedicineIds 原药品申请主键集合
     * @return 退药关联申请集合
     */
    List<MedicationRequest> findByRefundMedicineIdIn(Collection<Long> refundMedicineIds);

    /**
     * 按就诊主键集合与请求状态批量查询（护士站待校对计数等）。
     *
     * @param encounterIds 就诊主键集合
     * @param statusEnum     请求状态 code
     * @return 药品申请集合
     */
    List<MedicationRequest> findByEncounterIdInAndStatusEnum(Collection<Long> encounterIds, String statusEnum);

    /**
     * 按主键加悲观写锁（门诊执行次数门控 GAP-EXE-006）。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MedicationRequest> findWithLockById(Long id);

    /**
     * 按 based_on 锚点查询子嘱（住院长嘱改量链向下找子；皮试 companion 不在本表）。
     *
     * @param basedOnTable 来源表名（如 {@code med_medication_request}）
     * @param basedOnId    来源主键
     * @return 以该锚为 based_on 的药品申请（可能多条；改量场景通常至多一条有效链尾）
     */
    List<MedicationRequest> findByBasedOnTableAndBasedOnId(String basedOnTable, Long basedOnId);

    /**
     * 按 based_on 锚点批量查询子嘱（改量链批量装配）。
     *
     * @param basedOnTable 来源表名
     * @param basedOnIds   来源主键集合
     * @return 子嘱集合
     */
    List<MedicationRequest> findByBasedOnTableAndBasedOnIdIn(String basedOnTable, Collection<Long> basedOnIds);
}
