/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.orderexecute.support;

import com.zxplat.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecutePatientGroupDto;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Collections;
import java.util.List;

/**
 * 医嘱执行列表读结果（IF-IP-EXE-01）。
 */
@Data
@Accessors(chain = true)
public class NurseStationOrderExecuteListResult {

    /** 按就诊分组的医嘱执行行；无命中时为空列表。 */
    private List<NurseStationOrderExecutePatientGroupDto> groups;

    /**
     * 空结果工厂（groups 为不可变空列表）。
     *
     * @return 空读结果
     */
    public static NurseStationOrderExecuteListResult empty() {
        NurseStationOrderExecuteListResult result = new NurseStationOrderExecuteListResult();
        result.setGroups(Collections.emptyList());
        return result;
    }
}
