/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.job;

/**
 * 后台任务接口
 *
 * <p>所有后台任务都应实现此接口，任务会自动注册到Quartz调度器。</p>
 *
 * @author OpenHIS Contributors
 */
public interface IJob {

    /**
     * 执行任务
     *
     * @param context 任务上下文，包含任务参数等信息
     * @throws Exception 任务执行异常
     */
    void execute(JobContext context) throws Exception;

    /**
     * 获取任务名称（用于日志和监控）
     *
     * @return 任务名称
     */
    default String getJobName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 获取任务描述
     *
     * @return 任务描述
     */
    default String getJobDescription() {
        return getJobName();
    }
}
