/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.print.printtemplate.repository;

import com.zxplat.domain.print.printtemplate.entity.PrtTemplate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 画布打印模板仓储（PrtTemplate 聚合）。
 */
public interface PrtTemplateRepository extends JpaRepository<PrtTemplate, Long> {

    /**
     * 取租户+busNo+状态下版本最高的一行。
     *
     * @param tenantId 租户
     * @param busNo    业务键
     * @param status   状态码
     * @return 模板行或 null
     */
    PrtTemplate findFirstByTenantIdAndBusNoAndStatusAndIsDeletedFalseOrderByVersionDesc(
        Long tenantId, String busNo, String status);

    /**
     * 列出租户下全部未删模板，按 busNo 升序、版本降序。
     *
     * @param tenantId 租户
     * @return 模板行列表
     */
    List<PrtTemplate> findByTenantIdAndIsDeletedFalseOrderByBusNoAscVersionDesc(Long tenantId);

    /**
     * 列出租户下非指定状态的未删模板，按 busNo 升序、版本降序（默认列表排除 archived，BR-PRT-012）。
     *
     * @param tenantId       租户
     * @param excludedStatus 排除的状态码
     * @return 模板行列表
     */
    List<PrtTemplate> findByTenantIdAndStatusNotAndIsDeletedFalseOrderByBusNoAscVersionDesc(
        Long tenantId, String excludedStatus);

    /**
     * 列出租户+busNo+状态下全部未删行。
     *
     * @param tenantId 租户
     * @param busNo    业务键
     * @param status   状态码
     * @return 模板行列表
     */
    List<PrtTemplate> findAllByTenantIdAndBusNoAndStatusAndIsDeletedFalse(
        Long tenantId, String busNo, String status);

    /**
     * 列出租户+机构+busNo+状态下全部未删行（publish 归档同 scope published）。
     *
     * @param tenantId       租户
     * @param organizationId 机构
     * @param busNo          业务键
     * @param status         状态码
     * @return 模板行列表
     */
    List<PrtTemplate> findAllByTenantIdAndOrganizationIdAndBusNoAndStatusAndIsDeletedFalse(
        Long tenantId, Long organizationId, String busNo, String status);

    /**
     * 列出租户+busNo 下全部未删行（软删整组模板）。
     *
     * @param tenantId 租户
     * @param busNo    业务键
     * @return 模板行列表
     */
    List<PrtTemplate> findAllByTenantIdAndBusNoAndIsDeletedFalse(Long tenantId, String busNo);

    /**
     * 取租户+场景码+状态下版本最高的一行。
     *
     * @param tenantId     租户
     * @param scenarioCode 场景码
     * @param status       状态码
     * @return 模板行或 null
     */
    PrtTemplate findFirstByTenantIdAndScenarioCodeAndStatusAndIsDeletedFalseOrderByVersionDesc(
        Long tenantId, String scenarioCode, String status);

    /**
     * 列出租户+场景码下全部未删行（场景码收回）。
     *
     * @param tenantId     租户
     * @param scenarioCode 场景码
     * @return 模板行列表
     */
    List<PrtTemplate> findAllByTenantIdAndScenarioCodeAndIsDeletedFalse(
        Long tenantId, String scenarioCode);

    /**
     * 取租户下 busNo 前缀匹配的最大 busNo（日序编号生成）。
     *
     * @param tenantId       租户
     * @param busNoDayPrefix 当日前缀，如 PT20260628
     * @return 最大 busNo 行或 null
     */
    PrtTemplate findFirstByTenantIdAndBusNoStartingWithAndIsDeletedFalseOrderByBusNoDesc(
        Long tenantId, String busNoDayPrefix);
}
