/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.gateway.support;

import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** ELEP 应用层操作人解析。 */
@Component
@RequiredArgsConstructor
public class ElepCurrentOperatorResolver {

    private final ICurrentUser currentUser;

    public Long requireOperatorId() {
        Long uid = currentUser.getCurrentUserId();
        if (uid == null) {
            throw new BusinessException("AUTH_REQUIRED", "请先登录后再操作电子处方");
        }
        return uid;
    }
}
