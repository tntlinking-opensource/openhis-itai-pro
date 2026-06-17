package com.openhis.whale.booking.domain.schedule.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 日程替诊读模型（队列/大屏展示）。
 */
@Getter
@Setter
public class ScheduleSubstituteInfoModel {

    private Long originalPractitionerId;
    private Long substitutePractitionerId;
    private String substituteReason;
}
