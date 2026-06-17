package com.openhis.whale.booking.contracts.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 岗位出诊排班项（由 health PractitionerRole 排班子表映射）。
 */
@Getter
@Setter
public class PractitionerRoleScheduleItemDto {

    /** 岗位主键（导入 T2 追溯）。 */
    private Long assignmentId;

    /** 星期（1=周一 … 7=周日）。 */
    private Integer dayOfWeek;

    /** 时段 JSON 快照。 */
    private String timePeriodsJson;

    /** 诊室/位置主键。 */
    private Long locationId;

    /** 就诊类型主键。 */
    private Long healthcareServiceId;
}
