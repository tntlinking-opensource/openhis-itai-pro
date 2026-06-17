package com.openhis.whale.booking.application.template.response;

import lombok.Getter;
import lombok.Setter;

/**
 * T1 放号时段项（结构化出参，07 §5.2）。
 */
@Getter
@Setter
public class SlotProvisionTemplateItemDto {

    /** 开始时刻 HH:mm。 */
    private String startTime;

    /** 结束时刻 HH:mm。 */
    private String endTime;

    /** 容量。 */
    private Integer capacity;
}
