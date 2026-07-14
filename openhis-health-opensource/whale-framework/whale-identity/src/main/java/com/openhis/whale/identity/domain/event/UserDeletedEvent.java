/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.domain.event;

import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 平台用户软删事件：供 health 等模块联动注销绑定 {@code adm_practitioner}。
 */
@Getter
@Setter
public class UserDeletedEvent extends DomainEvent {

    /** 被软删的用户主键。 */
    private Long deletedUserId;

    /** 执行删除的操作人（可为 {@code null}）。 */
    private Long operatorId;

    /**
     * 默认构造（序列化/框架反射）。
     */
    public UserDeletedEvent() {
        super();
    }

    /**
     * @param deletedUserId 被软删用户主键
     * @param operatorId    操作人主键
     */
    public UserDeletedEvent(Long deletedUserId, Long operatorId) {
        super(deletedUserId, "User");
        this.deletedUserId = deletedUserId;
        this.operatorId = operatorId;
    }
}
