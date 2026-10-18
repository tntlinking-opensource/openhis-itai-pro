/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdispense.repository;

import com.zxplat.domain.medication.medicationdispense.entity.MedicationDispense;
import com.zxplat.domain.share.enums.DispenseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

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
     * 按汇总号集合批量查询未删除发放记录（汇总发药门控读侧批量加载）。
     *
     * @param summaryNos 汇总号集合
     * @return 发放记录集合
     */
    List<MedicationDispense> findBySummaryNoIn(Collection<String> summaryNos);

    /**
     * 按业务单号集合批量查询未删除发放记录（追溯报表患者 enrich · AS-CUS-TRC-002）。
     *
     * @param busNos 业务单号集合
     * @return 发放记录集合
     */
    List<MedicationDispense> findByBusNoInAndIsDeletedFalse(Collection<String> busNos);

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
     * 判断同品同批号是否存在未软删发药行（任意状态；BR-WHS-IN-018 同批号异价门禁）。
     *
     * @param medicationId 药品定义主键
     * @param lotNumber    批号字面值
     * @return 存在则为 true
     */
    boolean existsByMedicationIdAndLotNumberAndIsDeletedFalse(Long medicationId, String lotNumber);

    /**
     * 按执行记录主键集合批量查询未删除发放记录（取消执行 Dispense 分轨定位，DM-MED-002 §6.2.14）。
     *
     * @param procedureIds 执行记录主键集合
     * @return 发放记录集合；{@code procedureIds} 为空时返回空列表
     */
    List<MedicationDispense> findByProcedureIdIn(Collection<Long> procedureIds);

    /**
     * 按主键集合、源态与软删标记批量加载（汇总领药前置筛选，GAP-INV-003）。
     *
     * @param ids        主键集合
     * @param statusEnum 源态 {@code PREPARATION}
     * @return 未软删且状态匹配的发放记录
     */
    List<MedicationDispense> findByIdInAndStatusEnumAndIsDeletedFalse(
        Collection<Long> ids,
        DispenseStatus statusEnum);

    /**
     * 按汇总号、源态与软删标记批量加载（汇总核发前置筛选，GAP-INV-004）。
     *
     * @param summaryNo  汇总号
     * @param statusEnum 源态 {@code SUMMARIZED}
     * @return 未软删且状态匹配的发放记录
     */
    List<MedicationDispense> findBySummaryNoAndStatusEnumAndIsDeletedFalse(
        String summaryNo,
        DispenseStatus statusEnum);

    /**
     * 本库是否已有已发药类发放（GAP-OPN-004 发放侧窄口径：COMPLETED/PART_COMPLETED）。
     *
     * @param locationId  发药库房
     * @param statusEnums 状态集合
     * @return 存在则为 true
     */
    boolean existsByLocationIdAndStatusEnumInAndIsDeletedFalse(
        Long locationId, Collection<DispenseStatus> statusEnums);

    /**
     * 按交账头加载已挂载发放行（撤回/驳回清挂载）。
     *
     * @param cashierSettlementId 交账头主键
     * @return 挂载发放行列表
     */
    List<MedicationDispense> findByCashierSettlementIdAndIsDeletedFalse(Long cashierSettlementId);
}
