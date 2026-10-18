/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.conditiondefinition.repository;

import com.zxplat.domain.clinical.conditiondefinition.entity.ConditionDefinition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 诊断定义仓储。
 */
public interface ConditionDefinitionRepository extends JpaRepository<ConditionDefinition, Long>, JpaSpecificationExecutor<ConditionDefinition> {
    /**
     * 按主键查询。
     * @param id 条件
     * @return 查询结果
     */
    Optional<ConditionDefinition> findById(Long id);

    /**
     * 判断是否存在满足条件的记录（按来源 + 编码唯一）。
     * @param sourceEnum 目录来源
     * @param conditionCode 诊断编码
     * @return 是否存在
     */
    boolean existsBySourceEnumAndConditionCode(String sourceEnum, String conditionCode);

    /**
     * 判断是否存在「他行」占用同一来源+编码。
     * @param sourceEnum 目录来源
     * @param conditionCode 诊断编码
     * @param id 当前编辑主键
     * @return 是否存在
     */
    boolean existsBySourceEnumAndConditionCodeAndIdNot(String sourceEnum, String conditionCode, Long id);

    /**
     * 按来源 + 编码精确查询。
     * @param sourceEnum 目录来源
     * @param conditionCode 诊断编码
     * @return 命中实体
     */
    Optional<ConditionDefinition> findBySourceEnumAndConditionCode(String sourceEnum, String conditionCode);

    /**
     * 按来源 + 编码集合批量查询（国临导入 upsert 用）。
     * @param sourceEnum 目录来源
     * @param conditionCodes 编码集合
     * @return 命中列表
     */
    List<ConditionDefinition> findBySourceEnumAndConditionCodeIn(String sourceEnum, Collection<String> conditionCodes);

    /**
     * 按来源 + 国临码查询（对照维护：清理/同步 clinic_code 挂接）。
     */
    List<ConditionDefinition> findBySourceEnumAndClinicCode(String sourceEnum, String clinicCode);

    /**
     * 按来源 + 医保码查询（对照维护：clinical_v2 选医保后回写诊断行）。
     */
    List<ConditionDefinition> findBySourceEnumAndYbNo(String sourceEnum, String ybNo);

    /**
     * 按来源 + 医保码集合批量查询（医保目录列表反查国临对照）。
     *
     * @param sourceEnum 目录来源（通常 {@code clinical_v2}）
     * @param ybNos      医保码集合
     * @return 命中列表（同 yb_no 可多行）
     */
    List<ConditionDefinition> findBySourceEnumAndYbNoIn(String sourceEnum, Collection<String> ybNos);

    /**
     * 按对侧挂接字段 {@code clinic_code} 批量查询（医保目录已写国临码时的补齐路径）。
     *
     * @param clinicCodes 国临码集合
     * @return 命中列表（可含多来源；查询侧须排除 clinical_v2 自身）
     */
    List<ConditionDefinition> findByClinicCodeIn(Collection<String> clinicCodes);

    /**
     * 按主码 {@code condition_code} 批量查询（中西医归类、医保名反查等同码扫描）。
     *
     * @param conditionCodes 编码集合
     * @return 命中列表
     */
    List<ConditionDefinition> findByConditionCodeIn(Collection<String> conditionCodes);

    /**
     * 按条件查询。
     * @param ids 条件
     * @return 查询结果
     */
    List<ConditionDefinition> findByIdIn(List<Long> ids);

    /**
     * 按条件查询。
     * @param name 条件
     * @param conditionCode 条件
     * @param pageable 条件
     * @return 查询结果
     */
    Page<ConditionDefinition> findByNameContainingOrConditionCodeContaining(String name, String conditionCode, Pageable pageable);
}
