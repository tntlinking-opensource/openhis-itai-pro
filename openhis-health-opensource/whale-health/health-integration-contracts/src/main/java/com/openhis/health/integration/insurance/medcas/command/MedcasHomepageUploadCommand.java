/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.medcas.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 4401 病案首页上传命令。
 */
@Getter
@Setter
public class MedcasHomepageUploadCommand {

    private String contractBusNo;
    private String inputJson;
    private Long operatorId;
}
