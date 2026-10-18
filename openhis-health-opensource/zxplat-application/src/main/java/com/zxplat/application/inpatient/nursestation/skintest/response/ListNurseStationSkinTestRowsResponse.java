/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.skintest.response;

import com.zxplat.application.inpatient.nursestation.skintest.dto.NurseStationSkinTestRowDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * IF-IP-STK-02 皮试列表响应。
 */
@Getter
@Setter
public class ListNurseStationSkinTestRowsResponse {

    /** 皮试列表行。 */
    private List<NurseStationSkinTestRowDto> rows;
}
