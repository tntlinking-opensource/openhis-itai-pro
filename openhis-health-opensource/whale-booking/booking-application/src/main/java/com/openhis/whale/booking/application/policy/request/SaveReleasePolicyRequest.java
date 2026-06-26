package com.openhis.whale.booking.application.policy.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存放号策略请求。
 */
@Getter
@Setter
public class SaveReleasePolicyRequest {

    /** 主键（更新时）。 */
    private Long id;

    /** 科室/机构主键。 */
    private Long organizationId;

    /** 策略编码（可选；留空时由领域层自动生成，见 BookingTemplateCodeConstants#PREFIX_RELEASE_POLICY）。 */
    private String policyCode;

    /** 策略名称。 */
    @NotBlank
    private String policyName;

    /** 提前放号天数。 */
    @NotNull
    private Integer advanceDays;

    /** 号段时长（分钟）。 */
    @NotNull
    private Integer slotMinutes;

    /** 每号段容量。 */
    @NotNull
    private Integer capacityPerSlot;

    /** 渠道配额 JSON。 */
    private String channelQuotaJson;
}
