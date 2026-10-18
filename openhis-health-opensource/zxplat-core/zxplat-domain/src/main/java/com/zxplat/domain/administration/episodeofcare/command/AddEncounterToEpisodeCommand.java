/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.episodeofcare.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 将就诊纳入疗程命令：建立疗程与就诊的关联，用于跨次就诊归集。
 */
@Getter
@Setter
public class AddEncounterToEpisodeCommand {
    /** 疗程主键。 */
    private Long episodeOfCareId;
    /** 待纳入的就诊主键。 */
    private Long encounterId;
    /** 操作人主键。 */
    private Long operatorId;
}
