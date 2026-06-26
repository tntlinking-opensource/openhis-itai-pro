package com.openhis.whale.booking.application.template.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 保存 T2 排班日程模板。
 */
@Getter
@Setter
public class SaveScheduleTemplateRequest {

    /** 主键（更新时）。 */
    private Long id;

    /** 科室主键。 */
    @NotNull
    private Long organizationId;

    /** 模板编码（可选；留空时由领域层自动生成，见 BookingTemplateCodeConstants）。 */
    private String templateCode;

    /** 模板名称。 */
    @NotBlank
    private String templateName;

    /** 模式 MODE-A/B/C。 */
    @NotBlank
    private String scheduleMode;

    /** 医师主键。 */
    private Long practitionerId;

    /** 诊室主键。 */
    private Long locationId;

    /** 号源类型主键。 */
    @NotNull
    private Long healthcareServiceId;

    /** T1 模板主键。 */
    private Long slotProvisionTemplateId;

    /** 放号策略主键。 */
    private Long defaultReleasePolicyId;

    /** 生效起。 */
    private LocalDate effectiveStart;

    /** 生效止。 */
    private LocalDate effectiveEnd;

    /** 优先级。 */
    private Integer priority;

    /** 来源岗位主键。 */
    private Long sourcePractitionerRoleId;

    /** 是否启用。 */
    @NotNull
    private Boolean enabled;
}
