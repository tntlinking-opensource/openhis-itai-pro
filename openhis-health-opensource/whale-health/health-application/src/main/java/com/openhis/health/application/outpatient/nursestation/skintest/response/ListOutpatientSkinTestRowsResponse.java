/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.nursestation.skintest.response;

import com.openhis.health.application.outpatient.nursestation.skintest.dto.OutpatientSkinTestRowDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * IF-OP-STK-02 皮试列表响应。
 */
@Getter
@Setter
public class ListOutpatientSkinTestRowsResponse {

    private List<OutpatientSkinTestRowDto> rows;
}
