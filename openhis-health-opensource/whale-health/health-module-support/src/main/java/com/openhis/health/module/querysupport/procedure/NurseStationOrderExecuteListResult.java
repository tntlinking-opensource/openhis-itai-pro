/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.module.querysupport.procedure;

import com.openhis.health.module.querysupport.procedure.dto.NurseStationOrderExecutePatientGroupDto;
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

    private List<NurseStationOrderExecutePatientGroupDto> groups;

    public static NurseStationOrderExecuteListResult empty() {
        NurseStationOrderExecuteListResult result = new NurseStationOrderExecuteListResult();
        result.setGroups(Collections.emptyList());
        return result;
    }
}
