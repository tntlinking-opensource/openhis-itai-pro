/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.application.homepage.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 生成首页 PDF 请求。
 */
@Getter
@Setter
public class GenerateHomepagePdfRequest {

    /** 病案主档主键。 */
    private Long caseRecordId;
}
