/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.mrms.qc.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 手工登记质控缺陷请求。
 */
@Getter
@Setter
public class AddManualDefectRequest {

    /** 病案主档主键。 */
    private Long caseRecordId;

    /** 严重级别码（阻断/警告等）。 */
    private String severityCode;

    /** 缺陷描述信息。 */
    private String defectMessage;
}
