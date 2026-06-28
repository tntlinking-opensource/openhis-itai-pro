/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.mrms.homepage.dto;

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
}
