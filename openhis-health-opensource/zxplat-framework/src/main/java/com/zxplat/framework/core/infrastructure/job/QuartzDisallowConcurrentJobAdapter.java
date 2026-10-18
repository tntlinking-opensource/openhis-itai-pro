/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.job;

import org.quartz.DisallowConcurrentExecution;

/**
 * 禁止同一 JobDetail 并发执行的 Quartz 适配器。
 *
 * <p>与 {@link QuartzJobAdapter} 逻辑一致，仅增加 Quartz 并发互斥语义。</p>
 */
@DisallowConcurrentExecution
public class QuartzDisallowConcurrentJobAdapter extends QuartzJobAdapter {
}
