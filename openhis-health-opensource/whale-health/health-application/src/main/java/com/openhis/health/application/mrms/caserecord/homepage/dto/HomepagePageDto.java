/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.caserecord.homepage.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 首页编辑页 loadPage 出参。
 */
@Getter
@Setter
public class HomepagePageDto {

    /** 病案主档摘要。 */
    private CaseRecordDetailDto caseRecord;

    /** 首页数据。 */
    private HomepageDto homepage;

    /**
     * 是否启用「中医信息」段（由医院节点 {@code medins_type} 判定；与 homepage.tcmInfoEnabled 同口径）。
     */
    private Boolean tcmInfoEnabled;
}
