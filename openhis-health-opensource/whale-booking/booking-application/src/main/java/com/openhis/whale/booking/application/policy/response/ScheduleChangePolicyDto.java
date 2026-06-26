package com.openhis.whale.booking.application.policy.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 停诊变更策略 DTO（2.20）。
 */
@Getter
@Setter
public class ScheduleChangePolicyDto {

    /** 主键。 */
    private Long id;

    /** 科室/机构主键。 */
    private Long organizationId;

    /** 是否已落库（false 表示当前为内存默认）。 */
    private Boolean persisted;

    /** 停诊默认处置：TRANSFER / RESCHEDULE / CANCEL / MANUAL。 */
    private String onPractitionerStopMode;

    /** 转交匹配规则。 */
    private String transferMatchRule;

    /** 转交是否要求同号源类型。 */
    private Boolean transferRequireSameService;

    /** 是否允许跨医师转交。 */
    private Boolean transferAllowCrossPractitioner;

    /** 转交患者确认模式：NONE / SMS_CONFIRM / WINDOW_CONFIRM。 */
    private String transferPatientConfirmMode;

    /** 未处置预约时禁止完成停诊。 */
    private Boolean blockDeactivateIfUnhandled;

    /** 转交后是否通知患者。 */
    private Boolean notifyOnTransfer;

    /** 停诊取消预约时是否通知。 */
    private Boolean notifyOnCancelStop;

    /** 预览后冻结源 Slot 新预约。 */
    private Boolean freezeSourceSlotOnPreview;

    /** 冻结超时分钟数。 */
    private Integer freezeTtlMinutes;
}
