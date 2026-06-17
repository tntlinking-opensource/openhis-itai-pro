package com.openhis.whale.booking.application.policy.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 停诊变更策略 DTO（2.20）。
 */
@Getter
@Setter
public class ScheduleChangePolicyDto {

    private Long id;

    private Long organizationId;

    /** 是否已落库（false 表示当前为内存默认）。 */
    private Boolean persisted;

    private String onPractitionerStopMode;

    private String transferMatchRule;

    private Boolean transferRequireSameService;

    private Boolean transferAllowCrossPractitioner;

    private String transferPatientConfirmMode;

    private Boolean blockDeactivateIfUnhandled;

    private Boolean notifyOnTransfer;

    private Boolean notifyOnCancelStop;

    private Boolean freezeSourceSlotOnPreview;

    private Integer freezeTtlMinutes;
}
