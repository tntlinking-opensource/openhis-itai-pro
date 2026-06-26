/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.medicalinfo.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class QueryOpspDrug5205Request extends MedicalInfoQueryBaseRequest {

    /** yyyy-MM-dd HH:mm:ss */
    private String begntime;
    /** yyyy-MM-dd HH:mm:ss */
    private String endtime;
}
