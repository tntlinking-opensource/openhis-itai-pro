/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.medicalinfo.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/** 【5301】人员慢特病备案查询请求。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class QueryOpspFiling5301Request extends MedicalInfoQueryBaseRequest {
}
