/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.support;

import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 住院滚费落账前，将计费人绑定用户写入 {@link SecurityContextHolder}，
 * 使 JPA 审计 {@code created_by}/{@code updated_by} 落成计费人用户主键（非登录人、非种子 admin）。
 */
@Component
@RequiredArgsConstructor
public class AutoRollBillingActorSecuritySupport {

    private final PractitionerQueryService practitionerQueryService;

    /**
     * 以计费人医务人员绑定的系统用户身份执行落账。
     *
     * @param billingPractitionerId 计费人 {@code adm_practitioner.id}；须已绑定 {@code user_id}
     * @param action                落账动作
     * @param <T>                   返回类型
     * @return 动作返回值
     * @throws IllegalStateException 计费人不存在或未绑定系统用户
     */
    public <T> T runAsBillingPractitioner(Long billingPractitionerId, Supplier<T> action) {
        if (billingPractitionerId == null) {
            throw new IllegalStateException("住院滚费计费人未指定");
        }
        Practitioner practitioner = practitionerQueryService.findById(billingPractitionerId);
        if (practitioner == null || Boolean.TRUE.equals(practitioner.getIsDeleted())) {
            throw new IllegalStateException(
                "住院滚费计费人医务人员不存在 practitionerId=" + billingPractitionerId);
        }
        Long userId = practitioner.getUserId();
        if (userId == null) {
            throw new IllegalStateException(
                "住院滚费计费人未绑定系统用户 practitionerId=" + billingPractitionerId);
        }
        String displayName = practitioner.getName();
        if (StringUtils.isEmpty(displayName)) {
            displayName = "billing-practitioner-" + billingPractitionerId;
        }
        Authentication previous = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = new UserPrincipal(userId, displayName, "", true, billingPractitionerId);
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }
}
