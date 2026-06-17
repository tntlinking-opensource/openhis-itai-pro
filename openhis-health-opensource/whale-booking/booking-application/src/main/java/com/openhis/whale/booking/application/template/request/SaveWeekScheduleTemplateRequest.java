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

    private Long id;

    @NotNull
    private Long organizationId;

    @NotBlank
    private String templateCode;

    @NotBlank
    private String templateName;

    private Boolean fallbackBookable;

    private Boolean isDefault;

    @NotNull
    private Boolean enabled;

    private LocalDate effectiveStart;

    private LocalDate effectiveEnd;

    private List<SaveWeekScheduleTemplateItemRequest> items;
}
