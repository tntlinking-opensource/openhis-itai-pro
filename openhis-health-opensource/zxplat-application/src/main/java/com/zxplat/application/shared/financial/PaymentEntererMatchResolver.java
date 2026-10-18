/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.financial;

import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * payment / charge {@code enterer_id} 读侧匹配：仅 {@code adm_practitioner.id}（**BR-OPC-016**）。
 *
 * <p>写路径与库内口径均为医务人员主键；漏网 {@code zxplat_users.id} 由 Flyway
 * {@code V23__backfill_enterer_id_practitioner} 一次性回填，运行时<strong>不再</strong>按用户主键扩白名单。</p>
 */
@Component
@RequiredArgsConstructor
public class PaymentEntererMatchResolver {

    /** 医务人员读侧：校验 enterer 是否为有效 Practitioner 主键。 */
    private final PractitionerQueryService practitionerQueryService;

    /**
     * 解析经办人 Practitioner 对应的 payment {@code enterer_id} 白名单。
     *
     * @param cashierPractitionerId 经办人 Practitioner 主键
     * @return 去重后的 enterer 匹配主键；入参为空时返回空列表
     */
    public List<Long> resolveEntererMatchIds(Long cashierPractitionerId) {
        return this.resolveEntererMatchIds(cashierPractitionerId, null);
    }

    /**
     * 解析日结/费用明细经办人 {@code enterer_id} 白名单（仅 Practitioner 主键）。
     *
     * <p>{@code sessionUserId} 保留方法签名供日结调用方透传，本方法忽略（不再按登录用户主键扩匹配）。</p>
     *
     * @param cashierPractitionerId 所选经办人 Practitioner 主键
     * @param sessionUserId         当前登录用户主键（忽略）
     * @return 去重后的 enterer 匹配主键
     */
    public List<Long> resolveEntererMatchIds(Long cashierPractitionerId, Long sessionUserId) {
        if (cashierPractitionerId == null) {
            return List.of();
        }
        // BR-OPC-016：enterer_id 仅医务人员主键；sessionUserId 不参与白名单
        return List.of(cashierPractitionerId);
    }

    /**
     * 多收款人合并 enterer 白名单（去重保序）。
     *
     * <p>{@code sessionUserId} 保留签名，忽略。</p>
     *
     * @param cashierPractitionerIds 经办人 Practitioner 主键集合
     * @param sessionUserId          当前登录用户主键（忽略）
     * @return 合并后的 enterer 匹配主键
     */
    public List<Long> resolveEntererMatchIds(Collection<Long> cashierPractitionerIds, Long sessionUserId) {
        if (cashierPractitionerIds == null || cashierPractitionerIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> matchIds = new LinkedHashSet<>();
        for (Long cashierPractitionerId : cashierPractitionerIds) {
            if (cashierPractitionerId == null) {
                continue;
            }
            matchIds.add(cashierPractitionerId);
        }
        return new ArrayList<>(matchIds);
    }

    /**
     * 将窗内 {@code payment.enterer_id} 规范为 Practitioner 主键（供收款人下拉 value）。
     *
     * <p>仅保留能按医务人员主键命中的 id；无法解析的 enterer 丢弃，避免脏值进入下拉。</p>
     *
     * @param entererIds 窗内去重后的 enterer_id（须为 practitioner.id）
     * @return Practitioner 主键列表（去重保序）；入参空则空列表
     */
    public List<Long> resolvePractitionerIdsFromEntererIds(Collection<Long> entererIds) {
        if (entererIds == null || entererIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> distinctEntererIds = new LinkedHashSet<>();
        for (Long entererId : entererIds) {
            if (entererId != null) {
                distinctEntererIds.add(entererId);
            }
        }
        if (distinctEntererIds.isEmpty()) {
            return List.of();
        }
        // Pre：仅按医务人员主键批量命中（BR-OPC-016；禁止再按 userId 反查）
        List<Practitioner> byPractitionerId =
            this.practitionerQueryService.findAllByIds(distinctEntererIds);
        Map<Long, Practitioner> practitionerById = new HashMap<>();
        for (Practitioner practitioner : byPractitionerId) {
            if (practitioner != null && practitioner.getId() != null) {
                practitionerById.put(practitioner.getId(), practitioner);
            }
        }
        LinkedHashSet<Long> practitionerIds = new LinkedHashSet<>();
        for (Long entererId : distinctEntererIds) {
            // 无法映射到医务人员的 enterer 不进入下拉
            if (practitionerById.containsKey(entererId)) {
                practitionerIds.add(entererId);
            }
        }
        return new ArrayList<>(practitionerIds);
    }
}
