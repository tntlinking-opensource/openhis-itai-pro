package com.openhis.whale.booking.contracts.reader;

import com.openhis.whale.booking.contracts.dto.ScheduleFormMasterDataSnapshot;

/**
 * 门诊排班主数据读 Port（Organization / Practitioner / Location / HealthcareService）。
 */
public interface HealthScheduleMasterDataReader {

    /**
     * 加载排班台表单初始化下拉数据。
     *
     * @param organizationId 科室主键；可空表示不过滤医师/诊室
     * @return 主数据快照
     */
    ScheduleFormMasterDataSnapshot loadOutpatientScheduleFormOptions(Long organizationId);
}
