/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.episodeofcare.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 疗程状态变更命令：通过动作编码驱动状态机迁移，可携带原因与发生时刻。
 */
@Getter
@Setter
public class ChangeEpisodeOfCareStatusCommand {
    /** 疗程主键。 */
    private Long episodeOfCareId;
    /** 状态机动作编码（与领域枚举或常量约定一致）。 */
    private String action;
    /** 变更原因。 */
    private String reason;
    /** 业务发生时间。 */
    private LocalDateTime occurredAt;
    /** 操作人主键。 */
    private Long operatorId;
}
