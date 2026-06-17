package com.openhis.whale.booking.application.policy.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存停诊变更策略请求（2.20）。
 */
@Getter
@Setter
public class SaveScheduleChangePolicyRequest {

    private Long id;

    @NotNull
    private Long organizationId;

    @NotNull
    private String onPractitionerStopMode;

    @NotNull
    private String transferMatchRule;

    @NotNull
    private Boolean transferRequireSameService;

    @NotNull
    private Boolean transferAllowCrossPractitioner;

    @NotNull
    private String transferPatientConfirmMode;

    @NotNull
    private Boolean blockDeactivateIfUnhandled;

    @NotNull
    private Boolean notifyOnTransfer;

    @NotNull
    private Boolean notifyOnCancelStop;

    @NotNull
    private Boolean freezeSourceSlotOnPreview;

    @NotNull
    private Integer freezeTtlMinutes;
}
