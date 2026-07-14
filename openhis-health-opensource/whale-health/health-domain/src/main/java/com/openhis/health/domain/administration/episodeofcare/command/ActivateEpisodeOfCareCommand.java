/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.episodeofcare.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 激活疗程（Episode of Care）命令：将疗程置为进行中并记录激活时刻。
 */
@Getter
@Setter
public class ActivateEpisodeOfCareCommand {
    /** 疗程主键。 */
    private Long episodeOfCareId;
    /** 激活时间。 */
    private LocalDateTime activatedAt;
    /** 操作人主键。 */
    private Long operatorId;
}
