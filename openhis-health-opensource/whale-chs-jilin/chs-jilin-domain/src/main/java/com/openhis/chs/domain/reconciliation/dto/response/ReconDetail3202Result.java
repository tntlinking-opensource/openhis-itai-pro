/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.reconciliation.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class ReconDetail3202Result {

    private Long localRecordId;
    private String uploadFileQuryNo;
    private String uploadFilename;
    private String resultFileQuryNo;
    private String resultFilename;
    private String dldEndtime;
    private Integer rowCount;
    private Integer mismatchCount;
    private List<ReconDetailResultRowDto> resultRows;
}
