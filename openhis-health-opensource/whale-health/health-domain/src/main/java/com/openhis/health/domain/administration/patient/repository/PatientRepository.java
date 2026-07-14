/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.patient.repository;

import com.openhis.health.domain.administration.patient.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 患者聚合根仓储。
 */
public interface PatientRepository extends JpaRepository<Patient, Long>, JpaSpecificationExecutor<Patient> {

    /**
     * 查询未删除的患者。
     *
     * @param id 患者标识
     * @return 患者实体
     */
    Optional<Patient> findById(Long id);

    /**
     * 判断身份证号是否已存在。
     *
     * @param idCard 身份证号
     * @return 已存在返回 true
     */
    boolean existsByIdCard(String idCard);

    /**
     * 判断排除指定患者后身份证号是否冲突。
     *
     * @param idCard 身份证号
     * @param id 患者标识
     * @return 存在冲突返回 true
     */
    boolean existsByIdCardAndIdNot(String idCard, Long id);

    /**
     * 按身份证号查询最新患者。
     *
     * @param idCard 身份证号
     * @return 患者实体
     */
    List<Patient> findAllByIdCardOrderByCreatedAtDesc(String idCard);

    /**
     * 批量查询未删除患者。
     *
     * @param ids 患者标识集合
     * @return 患者集合
     */
    List<Patient> findAllByIdIn(Collection<Long> ids);

    /**
     * 查询同名同性别候选患者。
     *
     * @param name 姓名
     * @param genderEnum 性别编码
     * @implNote 在大医院场景，同名同性别候选可能达到数百条。
     *           调用方应优先走身份证号等精确匹配路径短路，避免高扇出全量加载。
     * @return 候选列表
     */
    List<Patient> findAllByNameAndGenderEnum(String name, String genderEnum);

    /**
     * 查询同名同性别且生日相同候选患者。
     *
     * @param name 姓名
     * @param genderEnum 性别编码
     * @param birthDate 出生日期
     * @implNote 尽管已增加出生日期过滤，候选集规模仍受基础数据质量影响。
     *           调用方应保留身份证号精确匹配优先的短路策略。
     * @return 候选列表
     */
    List<Patient> findAllByNameAndGenderEnumAndBirthDate(String name, String genderEnum, LocalDate birthDate);

    /**
     * 按状态集合排除并按创建时间倒序查询患者。
     *
     * @param idCard 身份证号
     * @param statusEnum 状态集合
     * @return 候选患者列表
     */
    List<Patient> findAllByIdCardAndStatusEnumNotInOrderByCreatedAtDesc(String idCard, Collection<String> statusEnum);
}
