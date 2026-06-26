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
public class QueryDise5202Request extends MedicalInfoQueryBaseRequest {

    private String mdtrtId;
}
