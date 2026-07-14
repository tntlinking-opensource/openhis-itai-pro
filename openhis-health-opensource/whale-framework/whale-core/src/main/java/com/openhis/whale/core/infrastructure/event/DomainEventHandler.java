/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.event;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * 领域事件处理器注解
 * 参考 ABP.io 的 @DomainEventHandler
 *
 * <p>标记领域事件处理器方法，方法应接受一个 DomainEvent 子类作为参数</p>
 *
 * <p>使用示例：</p>
 * <pre>
 * {@code
 * @Component
 * public class ActivityDefinitionEventHandler {
 *
 *     @DomainEventHandler
 *     public void handleCreated(ActivityDefinitionCreatedEvent event) {
 *         // 处理事件
 *     }
 * }
 * }
 * </pre>
 *
 * @author Whale Framework
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface DomainEventHandler {

    /**
     * 事件处理顺序（数字越小越先执行）
     *
     * @return 顺序值
     */
    @AliasFor(annotation = Component.class)
    int order() default 0;

    /**
     * 是否异步处理
     *
     * @return true 表示异步处理
     */
    boolean async() default false;
}
