/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.nursestation.skintest.response;

import com.zxplat.application.outpatient.nursestation.skintest.dto.OutpatientSkinTestRowDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * IF-OP-STK-02 皮试列表响应。
 */
@Getter
@Setter
public class ListOutpatientSkinTestRowsResponse {

    /** 皮试列表行。 */
    private List<OutpatientSkinTestRowDto> rows;
}
