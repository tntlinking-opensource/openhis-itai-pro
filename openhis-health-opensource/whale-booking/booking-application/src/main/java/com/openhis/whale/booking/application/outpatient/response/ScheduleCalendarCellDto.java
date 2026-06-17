package com.openhis.whale.booking.application.outpatient.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日历单元格内日程摘要。
 */
@Getter
@Setter
public class ScheduleCalendarCellDto {

    /** 日程主键。 */
    private Long scheduleId;

    /** 显示名称。 */
    private String name;

    /** 业务状态编码。 */
    private String statusEnum;

    /** 规划窗口起。 */
    private LocalDateTime planningHorizonStart;

    /** 规划窗口止。 */
    private LocalDateTime planningHorizonEnd;

    /** 关联医疗服务主键。 */
    private Long healthcareServiceId;

    /** 参与者摘要。 */
    private List<ScheduleActorDto> actors;

    /** 是否来自周模板 fallback（无 scheduleId）。 */
    private Boolean fromTemplate;

    /** 周模板主键。 */
    private Long weekScheduleTemplateId;

    /** 日模板主键。 */
    private Long dayScheduleTemplateId;

    /** 模板态是否可约（fallback_bookable）。 */
    private Boolean bookable;

    /** 替诊医师主键。 */
    private Long substitutePractitionerId;

    /** 原出诊医师主键。 */
    private Long originalPractitionerId;

    /** 替诊展示文案（如「B替A」）。 */
    private String substituteDisplay;
}
