/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.insurance.coordination.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** DISPENSE_UPLOAD 协同单上下文。 */
@Data
public class DispenseUploadCoordinationSnapshot {

    /** 就诊主键。 */
    private Long encounterId;

    /** 待发药/退药单据 id 列表。 */
    private List<Long> dispenseIds = new ArrayList<>();

    /** 上传类型：{@link com.openhis.health.domain.share.constants.InsuranceCoordinationConstants#DISPENSE_UPLOAD_KIND_SALE} 等。 */
    private String uploadKind;
}
