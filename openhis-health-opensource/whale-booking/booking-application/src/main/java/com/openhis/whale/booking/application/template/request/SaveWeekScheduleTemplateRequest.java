package com.openhis.whale.booking.application.template.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 保存 TW 周排班模板。
 */
@Getter
@Setter
public class SaveWeekScheduleTemplateRequest {

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

    /** 无 Schedule 时是否允许预约。 */
    private Boolean fallbackBookable;

    /** 科室默认周模板。 */
    private Boolean isDefault;

    /** 是否启用。 */
    @NotNull
    private Boolean enabled;

    /** 生效起。 */
    private LocalDate effectiveStart;

    /** 生效止。 */
    private LocalDate effectiveEnd;

    /** 周模板明细列表。 */
    private List<SaveWeekScheduleTemplateItemRequest> items;
}
