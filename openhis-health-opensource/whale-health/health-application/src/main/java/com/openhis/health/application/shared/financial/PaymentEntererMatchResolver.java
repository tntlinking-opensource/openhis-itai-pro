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
import java.util.LinkedHashSet;
import java.util.List;

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
}
