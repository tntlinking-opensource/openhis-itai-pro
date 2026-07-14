/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.domain.event;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 应用事件基类
 * 参考 ABP.io 的 ApplicationEvent 设计
 *
 * <p>应用事件用于跨边界上下文通信，通常不直接关联聚合根</p>
 *
 * <p>使用场景：</p>
 * <ul>
 *   <li>系统级别的通知</li>
 *   <li>跨模块的集成事件</li>
 *   <li>异步任务触发</li>
 * </ul>
 *
 * @author Whale Framework
 */
@Getter
@Setter
public abstract class ApplicationEvent implements IEvent {

    /**
     * 事件ID（唯一标识）
     */
    private String eventId;

    /**
     * 事件发生时间
     */
    private LocalDateTime eventTime;

    /**
     * 事件来源（哪个服务/模块发布的事件）
     */
    private String source;

    /**
     * 默认构造函数
     * 自动设置事件ID和发生时间
     */
    public ApplicationEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.eventTime = LocalDateTime.now();
    }

    /**
     * 带来源信息的构造函数
     *
     * @param source 事件来源
     */
    public ApplicationEvent(String source) {
        this();
        this.source = source;
    }
}
