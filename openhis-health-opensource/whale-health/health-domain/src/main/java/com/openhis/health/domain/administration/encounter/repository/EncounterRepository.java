/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.repository;

import com.openhis.health.domain.administration.encounter.entity.Encounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 就诊聚合仓储。
 */
public interface EncounterRepository extends JpaRepository<Encounter, Long>, JpaSpecificationExecutor<Encounter> {

    /**
     * 按主键查询未删除记录。
     *
     * @param id 就诊标识
     * @return 就诊实体
     */
    Optional<Encounter> findById(Long id);

    /**
     * 按主键与就诊类别查询未删除记录（住院/门诊挂号等场景须与 {@code classEnum} 隔离）。
     *
     * @param id 就诊标识
     * @param classEnum 就诊类别编码（如 {@code IMP}、{@code AMB}）
     * @return 命中时返回实体，否则 empty
     */
    Optional<Encounter> findByIdAndClassEnum(Long id, String classEnum);

    /**
     * 按主键与就诊类别加悲观写锁加载（有申请住院登记防 PLANNED 双窗 TOCTOU，GAP-ATD-004）。
     *
     * @param id        就诊标识
     * @param classEnum 就诊类别编码（住院登记为 {@code IMP}）
     * @return 未软删就诊
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Encounter> findByIdAndClassEnumAndIsDeletedFalse(Long id, String classEnum);

    /**
     * 批量按主键与就诊类别查询未删除记录。
     *
     * @param ids 就诊标识集合
     * @param classEnum 就诊类别编码
     * @return 命中集合（不保证与入参顺序一致）
     */
    List<Encounter> findAllByIdInAndClassEnum(Collection<Long> ids, String classEnum);

    /**
     * 按业务号查询未删除记录。
     *
     * @param busNo 就诊业务号
     * @return 就诊实体
     */
    Optional<Encounter> findByBusNo(String busNo);

    /**
     * 判断业务号是否存在。
     *
     * @param busNo 就诊业务号
     * @return 存在返回 true
     */
    boolean existsByBusNo(String busNo);

    /**
     * 批量查询未删除就诊记录。
     *
     * @param ids 就诊标识集合
     * @return 就诊集合
     */
    List<Encounter> findAllByIdIn(Collection<Long> ids);

    /**
     * 按门诊锚点取最新一条未处于排除状态集合中的住院线就诊。
     *
     * <p>用于住院申请防重：同一 {@code ambEncounterId} 在 {@code IMP} 下仅允许一条未取消住院线。</p>
     *
     * @param ambEncounterId 源门诊就诊标识
     * @param classEnum 就诊类别（住院为 {@code IMP}）
     * @param excludedStatusEnums 排除的状态码集合（通常仅 {@code CANCELLED}）
     * @return 命中时返回最新一条，否则 empty
     */
    Optional<Encounter> findTopByAmbEncounterIdAndClassEnumAndStatusEnumNotInOrderByCreatedAtDesc(
        Long ambEncounterId,
        String classEnum,
        Collection<String> excludedStatusEnums);

    /**
     * 是否存在指定类别、状态不在排除集合中的未软删就诊（体检防重复在检）。
     *
     * @param patientId 患者主键
     * @param classEnum 就诊类别（体检为 {@code PE}）
     * @param excludedStatusEnums 排除的终态集合
     * @return 存在返回 true
     */
    boolean existsByPatientIdAndClassEnumAndStatusEnumNotInAndIsDeletedFalse(
        Long patientId,
        String classEnum,
        Collection<String> excludedStatusEnums);
}
