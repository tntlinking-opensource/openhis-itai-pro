/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.domain.catalog.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** 按编码精确查目录。 */
@Data
@Accessors(chain = true)
public class ElepCatalogCodeRequest {

    private Long contractId;
    private String contractBusNo;
    private String hilistCode;
    private String aprvno;
    private String spec;
}
