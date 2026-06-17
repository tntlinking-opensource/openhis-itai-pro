package com.openhis.whale.booking.application.outpatient.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 停诊影响预览响应（2.18）。
 */
@Getter
@Setter
public class PreviewStopClinicImpactResponse {

    /** 日程主键。 */
    private Long scheduleId;

    /** 影响日程数（整日停诊为 1）。 */
    private Integer scheduleCount;

    /** 影响号源数。 */
    private Integer slotCount;

    /** 有效预约数。 */
    private Integer appointmentCount;

    /** 已挂号数（跳过自动处置）。 */
    private Integer registeredCount;

    /** 已报到/已叫号数（跳过自动处置）。 */
    private Integer checkedInCount;

    /** 渠道预约数。 */
    private Integer channelCount;

    /** 未处置预约时是否禁止直接停诊。 */
    private Boolean blockDeactivateIfUnhandled;

    /** 院点默认处置方式。 */
    private String defaultDispositionMode;

    /** 转交匹配规则。 */
    private String transferMatchRule;

    /** 预览后是否已冻结源号源。 */
    private Boolean sourceSlotsFrozen;

    /** 冻结号源数。 */
    private Integer frozenSlotCount;

    /** 冻结过期时间（最晚）。 */
    private LocalDateTime freezeExpiresAt;

    /** 预约明细与映射预览。 */
    private List<StopClinicImpactItemDto> items = new ArrayList<>();
}
