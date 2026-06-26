package com.openhis.whale.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 团检批次 DTO（6.x 旧版 API 别名，对应 GroupCheckupBatchDto）。
 */
@Getter
@Setter
public class GroupBatchDto {

    /** 团检批次主键。 */
    private Long id;

    /** 团检单位主键。 */
    private Long groupOrgId;

    /** 团检单位名称。 */
    private String groupOrgName;

    /** 批次名称。 */
    private String batchName;

    /** 预约日期。 */
    private LocalDate bookingDate;

    /** 号源开始时刻。 */
    private LocalTime slotStartTime;

    /** 容量。 */
    private Integer capacity;

    /** 已约人数。 */
    private Integer bookedCount;

    /** 套餐名称。 */
    private String packageName;
}
