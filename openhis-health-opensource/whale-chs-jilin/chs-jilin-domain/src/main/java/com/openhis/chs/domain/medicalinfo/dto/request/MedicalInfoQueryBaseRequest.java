/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.medicalinfo.dto.request;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class MedicalInfoQueryBaseRequest {

    private String contractBusNo;
    private Long contractId;
    private String psnNo;
}
