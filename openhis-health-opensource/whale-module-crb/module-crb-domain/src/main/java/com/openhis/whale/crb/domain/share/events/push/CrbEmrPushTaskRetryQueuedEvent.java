/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.share.events.push;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 推送任务人工重试入队事件（DM-CRB-PUSH-001 §5.1）。
 */
@Getter
@AllArgsConstructor
public class CrbEmrPushTaskRetryQueuedEvent {

    /** 任务主键。 */
    private final Long taskId;
}
