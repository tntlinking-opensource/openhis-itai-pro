/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.supplyrequest.repository;

import com.zxplat.domain.workflow.supplyrequest.entity.SupplyRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 供应请求聚合仓储（表 {@code wkf_supply_request}）。
 *
 * <p>约束：禁止 {@code @Query}；过滤与软删条件由方法名派生查询下推到数据库（项目 JPA 规约）。</p>
 *
 * <p>方法参数中 {@code statusEnum} 等与实体列一致，须传入各业务枚举的 {@link com.zxplat.domain.share.enums.EnumInterface#getCode()}
 * 持久化字符串（B.10.8.1），禁止传排序号。</p>
 */
public interface SupplyRequestRepository extends JpaRepository<SupplyRequest, Long>, JpaSpecificationExecutor<SupplyRequest> {

    /**
     * 按业务单号加载<strong>未软删</strong>行（同号多行；方法名显式 {@code IsDeletedFalse}，
     * 不依赖 Session Filter 是否启用，避免「软删重插后仍读到旧行」）。
     *
     * @param busNo 业务单号 {@code bus_no}
     * @return 行列表；无数据时返回空列表
     */
    List<SupplyRequest> findByBusNoAndIsDeletedFalse(String busNo);

    /**
     * 按业务单号加载行（含已软删）；仅审计/修复类场景；业务读请用 {@link #findByBusNoAndIsDeletedFalse(String)}。
     *
     * @param busNo 业务单号
     * @return 行列表
     */
    List<SupplyRequest> findByBusNo(String busNo);

    /**
     * 按多个业务单号批量加载<strong>未软删</strong>行。
     *
     * @param busNoList 业务单号集合；空集合行为由调用方规避
     * @return 命中行列表
     */
    List<SupplyRequest> findByBusNoInAndIsDeletedFalse(List<String> busNoList);

    /**
     * 按多个业务单号批量加载（含已软删）；业务读请用 {@link #findByBusNoInAndIsDeletedFalse(List)}。
     *
     * @param busNoList 业务单号集合
     * @return 命中行列表
     */
    List<SupplyRequest> findByBusNoIn(List<String> busNoList);

    /**
     * 按来源/原始业务单号加载行（如订货号、关联原单号）。
     *
     * @param originalBusNo {@code original_bus_no}
     * @return 行列表
     */
    List<SupplyRequest> findByOriginalBusNo(String originalBusNo);

    /**
     * 判断是否存在未软删行以 {@code original_bus_no} 引用来源单且类型匹配（用于订货→进货互斥等门控）。
     *
     * @param originalBusNo 来源业务单号
     * @param typeEnum      {@code type_enum} 持久化 code
     * @return 存在任一行则为 {@code true}
     */
    boolean existsByOriginalBusNoAndTypeEnum(String originalBusNo, String typeEnum);

    /**
     * 判断是否存在未软删行以 {@code original_bus_no} 引用来源单、类型匹配且 {@code bus_no} 不等于排除单号（改单自引用豁免）。
     *
     * @param originalBusNo 来源业务单号
     * @param typeEnum      {@code type_enum} 持久化 code
     * @param busNo         改单时排除的本单 {@code bus_no}
     * @return 存在其它单号占用则为 {@code true}
     */
    boolean existsByOriginalBusNoAndTypeEnumAndBusNoNot(String originalBusNo, String typeEnum, String busNo);

    /**
     * 按汇总主键集合加载（汇总类供应场景）。
     *
     * @param summaryIdList {@code summary_id} 列表
     * @return 行列表
     */
    List<SupplyRequest> findBySummaryIdIn(List<Long> summaryIdList);

    /**
     * 按主键集合批量加载。
     *
     * @param idList {@code id} 列表
     * @return 行列表
     */
    List<SupplyRequest> findByIdIn(List<Long> idList);

    /**
     * 按主键查询单条（与 {@link JpaRepository} 语义一致；若需软删过滤须在实体或规约层体现）。
     *
     * @param id 主键
     * @return 可选单条
     */
    Optional<SupplyRequest> findById(Long id);

    /**
     * 判断指定业务单号下是否已存在指定审批状态行（用于同意幂等等）。
     *
     * @param busNo       业务单号
     * @param statusEnum  {@code status_enum} 持久化值，如 {@link com.zxplat.domain.share.enums.SupplyStatus#AGREE#getCode()}
     * @return 存在任一行则为 {@code true}
     */
    boolean existsByBusNoAndStatusEnum(String busNo, String statusEnum);

    /**
     * 判断指定物品是否出现在审核中或已同意的供应单行（目录入库锁定门控）。
     *
     * @param itemId      物品主键 {@code item_id}
     * @param statusEnums 供应状态持久化码集合
     * @return 命中任一行时为 true
     */
    boolean existsByItemIdAndStatusEnumInAndIsDeletedFalse(Long itemId, Collection<String> statusEnums);

    /**
     * 同库（purpose）是否存在非 OPENING、指定状态集合的未删供应单（GAP-OPN-004 供应侧）。
     *
     * @param purposeLocationId 目的库房
     * @param typeEnums         排除的 OPENING 类型集合（NotIn）
     * @param statusEnums       已提交及以上状态集合
     * @return 存在则为 true
     */
    boolean existsByPurposeLocationIdAndTypeEnumNotInAndStatusEnumInAndIsDeletedFalse(
        Long purposeLocationId, Collection<String> typeEnums, Collection<String> statusEnums);

    /**
     * 同库首条非 OPENING、指定状态集合的未删供应行（期初业务封锁诊断用）。
     *
     * @param purposeLocationId 目的库房
     * @param typeEnums         排除的 OPENING 类型集合（NotIn）
     * @param statusEnums       已提交及以上状态集合
     * @return 命中行；无则 empty
     */
    Optional<SupplyRequest> findFirstByPurposeLocationIdAndTypeEnumNotInAndStatusEnumInAndIsDeletedFalseOrderByIdAsc(
        Long purposeLocationId, Collection<String> typeEnums, Collection<String> statusEnums);

    /**
     * 同库是否存在指定类型与状态的未删供应单（审核中闸门等）。
     *
     * @param purposeLocationId 目的库房
     * @param typeEnums         OPENING 类型
     * @param statusEnum        状态
     * @return 存在则为 true
     */
    boolean existsByPurposeLocationIdAndTypeEnumInAndStatusEnumAndIsDeletedFalse(
        Long purposeLocationId, Collection<String> typeEnums, String statusEnum);

    /**
     * 同库当前有效 AGREE 期初行（同号多行）。
     *
     * @param purposeLocationId 目的库房
     * @param typeEnums         OPENING 类型
     * @param statusEnum        {@code AGREE}
     * @return 行列表
     */
    List<SupplyRequest> findByPurposeLocationIdAndTypeEnumInAndStatusEnumAndIsDeletedFalse(
        Long purposeLocationId, Collection<String> typeEnums, String statusEnum);
}
