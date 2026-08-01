/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.financial;

import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * payment {@code enterer_id} 读侧匹配：Practitioner 主键 + 绑定用户主键（存量兼容）。
 *
 * <p>挂号/收费写路径以 {@code adm_practitioner.id} 落库为主；历史或异常数据可能仍为
 * {@code whale_users.id}。日结与费用明细按经办人筛选时须同时命中两类标识。</p>
 */
@Component
@RequiredArgsConstructor
public class PaymentEntererMatchResolver {

    /** 医务人员读侧：解析 Practitioner 与绑定用户主键。 */
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
     * 解析日结/费用明细经办人 {@code enterer_id} 白名单（Practitioner 主键 + 绑定用户 + 会话用户存量）。
     *
     * <p>住院登记等历史写路径曾将 {@code enterer_id} 落为 {@code whale_users.id}（前端 {@code userInfo.id}），
     * 当 {@code sessionUserId} 映射到所选 {@code cashierPractitionerId} 时须一并纳入匹配。</p>
     *
     * @param cashierPractitionerId 所选经办人 Practitioner 主键
     * @param sessionUserId         当前登录用户主键，可空
     * @return 去重后的 enterer 匹配主键
     */
    public List<Long> resolveEntererMatchIds(Long cashierPractitionerId, Long sessionUserId) {
        if (cashierPractitionerId == null) {
            return List.of();
        }
        LinkedHashSet<Long> matchIds = new LinkedHashSet<>();
        matchIds.add(cashierPractitionerId);
        Practitioner practitioner = this.practitionerQueryService.findById(cashierPractitionerId);
        if (practitioner != null && practitioner.getUserId() != null) {
            // 存量 payment.enterer_id 可能存系统用户主键
            matchIds.add(practitioner.getUserId());
        }
        if (sessionUserId != null) {
            Practitioner mappedPractitioner = this.practitionerQueryService.findByUserId(sessionUserId);
            if (mappedPractitioner != null
                && mappedPractitioner.getId() != null
                && cashierPractitionerId.equals(mappedPractitioner.getId())) {
                // 住院登记/预交金存量：enterer_id 落登录用户主键，与 practitioner.user_id 未绑定时仍须命中
                matchIds.add(sessionUserId);
            }
        }
        return new ArrayList<>(matchIds);
    }

    /**
     * 多收款人合并 enterer 白名单（去重保序）。
     *
     * @param cashierPractitionerIds 经办人 Practitioner 主键集合
     * @param sessionUserId          当前登录用户主键，可空
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
            matchIds.addAll(this.resolveEntererMatchIds(cashierPractitionerId, sessionUserId));
        }
        return new ArrayList<>(matchIds);
    }

    /**
     * 将窗内 {@code payment.enterer_id} 混集规范为 Practitioner 主键（供收款人下拉 value）。
     *
     * <p>先按医务人员主键命中；未命中再按绑定用户主键反查。无法解析的 enterer 丢弃，
     * 避免把系统用户 id 直接塞进下拉导致软筛/提交对不上。</p>
     *
     * @param entererIds 窗内去重后的 enterer_id（可能混有 practitioner.id / whale_users.id）
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
        // Pre：先按医务人员主键批量命中
        List<Practitioner> byPractitionerId =
            this.practitionerQueryService.findAllByIds(distinctEntererIds);
        Map<Long, Practitioner> practitionerById = new HashMap<>();
        for (Practitioner practitioner : byPractitionerId) {
            if (practitioner != null && practitioner.getId() != null) {
                practitionerById.put(practitioner.getId(), practitioner);
            }
        }
        List<Long> unresolvedUserIds = new ArrayList<>();
        for (Long entererId : distinctEntererIds) {
            if (!practitionerById.containsKey(entererId)) {
                unresolvedUserIds.add(entererId);
            }
        }
        // Pre：剩余按绑定用户主键反查（存量 enterer_id=userId；含停用主档，只要曾收费即可带出）
        Map<Long, Practitioner> practitionerByUserId = new HashMap<>();
        for (Long userId : unresolvedUserIds) {
            Practitioner practitioner = this.practitionerQueryService.findByUserId(userId);
            if (practitioner == null || practitioner.getUserId() == null || practitioner.getId() == null) {
                continue;
            }
            practitionerByUserId.put(practitioner.getUserId(), practitioner);
        }
        LinkedHashSet<Long> practitionerIds = new LinkedHashSet<>();
        for (Long entererId : distinctEntererIds) {
            Practitioner byId = practitionerById.get(entererId);
            if (byId != null && byId.getId() != null) {
                practitionerIds.add(byId.getId());
                continue;
            }
            Practitioner byUser = practitionerByUserId.get(entererId);
            // 无法映射到医务人员的 enterer 不进入下拉
            if (byUser != null && byUser.getId() != null) {
                practitionerIds.add(byUser.getId());
            }
        }
        return new ArrayList<>(practitionerIds);
    }
}
