/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.reconciliation.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class CrossProvinceReconRowDto {

    private String setlId;
    private String insuAdmdvs;
}
