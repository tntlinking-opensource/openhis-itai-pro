/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.reconciliation.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class LocalReconDetailDto {

    private Long id;
    private String contractBusNo;
    private String setlOptins;
    private String clrType;
    private String stmtBegndate;
    private String stmtEnddate;
    private Integer rowCount;
    private Integer mismatchCount;
    private String uploadFileQuryNo;
    private String resultFileQuryNo;
    private String createdAt;
}
