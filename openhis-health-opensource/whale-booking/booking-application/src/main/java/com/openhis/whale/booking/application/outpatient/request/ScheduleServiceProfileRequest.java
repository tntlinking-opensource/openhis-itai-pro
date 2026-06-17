package com.openhis.whale.booking.application.outpatient.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 日程服务口径结构化入参（出站前由 Mapper 序列化为领域 JSON 快照）。
 */
@Getter
@Setter
public class ScheduleServiceProfileRequest {

    /** 服务大类编码。 */
    private String serviceCategoryCode;

    /** 服务大类显示名。 */
    private String serviceCategoryDisplay;

    /** 专科编码。 */
    private String specialtyCode;

    /** 专科显示名。 */
    private String specialtyDisplay;

    /** 服务类型概念编码。 */
    private String serviceTypeCode;

    /** 服务类型概念显示名。 */
    private String serviceTypeDisplay;

    /** 关联医疗服务主键。 */
    private Long healthcareServiceId;

    /** 档案版本号（可选，用于事件对账）。 */
    private String profileVersion;
}
