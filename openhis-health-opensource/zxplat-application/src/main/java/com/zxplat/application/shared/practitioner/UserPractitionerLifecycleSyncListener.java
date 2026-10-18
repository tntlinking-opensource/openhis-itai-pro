/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.practitioner;

import com.zxplat.domain.administration.practitioner.service.PractitionerDomainService;
import com.zxplat.domain.share.constants.PractitionerLifecycleConstants;
import com.zxplat.framework.identity.domain.event.UserDeletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台用户生命周期与医务人员主档联动：用户软删时同步注销 {@code adm_practitioner}。
 *
 * <p>监听 identity {@link UserDeletedEvent}，写路径委托 {@code PractitionerDomainService}（B.6.4：不经 AppService 直写）。
 * 岗位行 {@code adm_pr_assign} 由后续 {@code PractitionerRetiredEvent} →
 * {@link PractitionerRoleLifecycleSyncListener} 级联废弃。</p>
 */
@Component
@RequiredArgsConstructor
public class UserPractitionerLifecycleSyncListener {

    private final PractitionerDomainService practitionerDomainService;

    /**
     * 用户软删后联动注销绑定医务人员（与 {@code UserAppService#delete} 同事务提交）。
     *
     * @param event 用户软删事件
     */
    @EventListener
    @Transactional
    public void onUserDeleted(UserDeletedEvent event) {
        // 事件载荷不完整时跳过，避免 NPE 误触发批量 retire
        if (event == null || event.getDeletedUserId() == null) {
            return;
        }
        // Cmd：按 user_id 批量 retire 未删 practitioner，原因见 PractitionerLifecycleConstants
        this.practitionerDomainService.retirePractitionerByBoundUserId(
            event.getDeletedUserId(),
            event.getOperatorId(),
            PractitionerLifecycleConstants.RETIRE_REASON_BOUND_USER_DELETED);
    }
}
