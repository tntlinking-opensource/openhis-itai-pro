/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.episodeofcare.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 新建疗程命令：为患者在某机构下开立疗程并分配业务单号。
 */
@Getter
@Setter
public class CreateEpisodeOfCareCommand {
    /** 患者主键。 */
    private Long patientId;
    /** 机构主键。 */
    private Long organizationId;
    /** 疗程业务单号（唯一性由领域/库约束保证）。 */
    private String busNo;
    /** 操作人主键。 */
    private Long operatorId;
}
