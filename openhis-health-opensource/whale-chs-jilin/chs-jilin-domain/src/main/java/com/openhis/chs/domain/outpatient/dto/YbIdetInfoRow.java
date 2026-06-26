/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.outpatient.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** 1101 身份信息行。 */
@Data
@Accessors(chain = true)
public class YbIdetInfoRow {

    private String psnIdetType;
    private String psnTypeLv;
    private String memo;
    private String begntime;
    private String endtime;
}
