/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.config;

import org.springframework.context.ApplicationEvent;

/**
 * 初始化完成事件
 * 当所有异步初始化任务完成后发布此事件
 * 用于通知应用初始化器显示启动信息
 */
public class InitializationCompletedEvent extends ApplicationEvent {

    public InitializationCompletedEvent(Object source) {
        super(source);
    }
}
