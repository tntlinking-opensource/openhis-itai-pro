package com.openhis.whale.booking.application.outpatient.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 T2 模板生成 Schedule 结果（2.17）。
 */
@Getter
@Setter
public class GenerateFromTemplateResponse {

    /** 新建日程主键列表。 */
    private List<Long> scheduleIds = new ArrayList<>();

    /** 成功生成天数。 */
    private Integer generatedCount;

    /** 因模板有效期外跳过天数。 */
    private Integer skippedCount;

    /** 自动放号数量（autoProvision=true 时）。 */
    private Integer provisionedSlotCount;
}
