/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.job;

/**
 * 后台任务执行完成回调，由业务模块实现并注册为 Spring Bean。
 */
public interface IJobExecutionListener {

    /**
     * 任务执行结束后回调（成功或失败均会触发）。
     *
     * @param report 执行结果快照
     */
    void onJobFinished(JobExecutionReport report);
}
