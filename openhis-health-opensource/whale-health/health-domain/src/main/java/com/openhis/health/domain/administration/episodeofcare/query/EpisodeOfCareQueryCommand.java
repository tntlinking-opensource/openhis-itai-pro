/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.episodeofcare.query;

import lombok.Getter;
import lombok.Setter;

/**
 * 疗程查询命令：按主键、业务单号、患者或机构筛选疗程列表/单条。
 */
@Getter
@Setter
public class EpisodeOfCareQueryCommand {
    /** 疗程主键。 */
    private Long id;
    /** 疗程业务单号。 */
    private String busNo;
    /** 患者主键。 */
    private Long patientId;
    /** 机构主键。 */
    private Long organizationId;
}
