/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
