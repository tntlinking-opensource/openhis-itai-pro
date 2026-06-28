/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.medcas.result;

import lombok.Getter;
import lombok.Setter;

/**
 * 病案首页 4401 上传结果（平台返回摘要）。
 */
@Getter
@Setter
public class MedcasUploadResult {

    private boolean success;
    private String infcode;
    private String errMsg;
    private String warnMsg;
    private String output;
    private String msgid;
}
