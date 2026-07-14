/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.administration.support;

import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.identity.application.UserAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 收费员/经办人下拉装配：排除绑定系统用户已软删或未启用的医务人员。
 *
 * <p>{@code adm_practitioner} 与 {@code whale_users} 主键独立；用户删除后 practitioner 可能仍为 ACTIVE，
 * 日结/费用明细经办人下拉须二次过滤 identity 用户态。</p>
 */
@Component
@RequiredArgsConstructor
public class PractitionerCashierOptionProvider {

    private final UserAppService userAppService;

    /**
     * 医务人员是否仍绑定有效系统用户（未软删且启用）。
     *
     * @param practitioner 医务人员主档
     * @return 可出现在经办人下拉时为 {@code true}
     */
    public boolean hasActiveBoundSystemUser(Practitioner practitioner) {
        if (practitioner == null || practitioner.getUserId() == null) {
            return false;
        }
        Set<Long> activeUserIds = this.resolveActiveBoundUserIds(Collections.singleton(practitioner.getUserId()));
        return activeUserIds.contains(practitioner.getUserId());
    }

    /**
     * 保留绑定有效系统用户的医务人员（原地顺序不变）。
     *
     * @param practitioners 候选医务人员
     * @return 过滤后列表；入参为空时返回空列表
     */
    public List<Practitioner> filterWithActiveBoundSystemUser(Collection<Practitioner> practitioners) {
        List<Practitioner> result = new ArrayList<>();
        if (practitioners == null || practitioners.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> userIds = new LinkedHashSet<>();
        for (Practitioner practitioner : practitioners) {
            if (practitioner != null && practitioner.getUserId() != null) {
                userIds.add(practitioner.getUserId());
            }
        }
        Set<Long> activeUserIds = this.resolveActiveBoundUserIds(userIds);
        for (Practitioner practitioner : practitioners) {
            if (practitioner == null || practitioner.getId() == null) {
                continue;
            }
            Long userId = practitioner.getUserId();
            // 绑定用户已软删或未启用时不进入经办人下拉（存量脏数据兜底，写路径见 UserDeletedEvent 联动）
            if (userId == null || !activeUserIds.contains(userId)) {
                continue;
            }
            result.add(practitioner);
        }
        return result;
    }

    /**
     * 医务人员列表 → 经办人 OptionDto（value 为 practitioner.id 字符串）。
     *
     * @param practitioners 已过滤的有效医务人员
     * @return 下拉选项
     */
    public List<OptionDto> toCashierOptions(Collection<Practitioner> practitioners) {
        List<OptionDto> options = new ArrayList<>();
        if (practitioners == null || practitioners.isEmpty()) {
            return options;
        }
        for (Practitioner practitioner : practitioners) {
            if (practitioner == null || practitioner.getId() == null) {
                continue;
            }
            if (StringUtils.isEmpty(practitioner.getName())) {
                continue;
            }
            OptionDto option = new OptionDto();
            option.setValue(String.valueOf(practitioner.getId()));
            option.setLabel(practitioner.getName());
            options.add(option);
        }
        return options;
    }

    /**
     * 用户 id 去重后过滤为仍激活且已绑定的 id 集合。
     *
     * @param userIds 候选用户 id
     * @return 激活绑定用户 id；空输入返回空集合
     */
    private Set<Long> resolveActiveBoundUserIds(Collection<Long> userIds) {
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        if (userIds != null) {
            for (Long userId : userIds) {
                if (userId != null) {
                    distinct.add(userId);
                }
            }
        }
        if (distinct.isEmpty()) {
            return Collections.emptySet();
        }
        return this.userAppService.findActiveBoundUserIds(distinct);
    }
}
