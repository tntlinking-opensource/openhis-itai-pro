/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.caserecord.homepage.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 加载首页编辑页请求。
 *
 * <p>{@code caseRecordId} 与 {@code encounterId} 二选一；二者皆有时优先 {@code caseRecordId}。
 * 仅传 {@code encounterId} 时会幂等 {@code bootstrapIfAbsent} 再建档后返回编辑页。</p>
 */
@Getter
@Setter
public class LoadPageRequest {

    /**
     * 病案主档主键；与 {@link #encounterId} 二选一，优先本字段。
     */
    private Long caseRecordId;

    /**
     * 住院就诊主键；无 {@link #caseRecordId} 时必填，用于首次打开时幂等建档。
     */
    private Long encounterId;
}
