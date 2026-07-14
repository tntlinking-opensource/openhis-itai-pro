/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.domain.event;

import com.openhis.whale.core.domain.IMultiTenant;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 领域事件基类
 * 参考 ABP.io 的 DomainEvent 设计
 *
 * <p>领域事件表示领域内发生的重要业务事件，通常与聚合根相关</p>
 *
 * <p>使用场景：</p>
 * <ul>
 *   <li>实体创建、更新、删除</li>
 *   <li>状态变更</li>
 *   <li>业务流程中的重要节点</li>
 * </ul>
 *
 * @author Whale Framework
 */
@Getter
@Setter
public abstract class DomainEvent implements IEvent, IMultiTenant {

    /**
     * 事件ID（唯一标识）
     */
    private String eventId;

    /**
     * 事件发生时间
     */
    private LocalDateTime eventTime;

    /**
     * 租户ID（多租户支持）
     */
    private Long tenantId;

    /**
     * 聚合根ID（触发事件的聚合根）
     */
    private Long aggregateId;

    /**
     * 聚合根类型（如 Patient, Encounter, ActivityDefinition 等）
     */
    private String aggregateType;

    /**
     * 操作用户ID（审计信息）
     */
    private Long userId;

    /**
     * 默认构造函数
     * 自动设置事件ID和发生时间
     */
    public DomainEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.eventTime = LocalDateTime.now();
    }

    /**
     * 带聚合根信息的构造函数
     *
     * @param aggregateId 聚合根ID
     * @param aggregateType 聚合根类型
     */
    public DomainEvent(Long aggregateId, String aggregateType) {
        this();
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
    }
}
