package com.openhis.whale.booking.application.medtech.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 医技资源映射 DTO（7.7）。
 */
@Getter
@Setter
public class DiagnosticResourceMapDto {

    /** 映射主键。 */
    private Long id;

    /** 机构/科室主键。 */
    private Long organizationId;

    /** 活动定义主键。 */
    private Long activityDefinitionId;

    /** 服务类别编码。 */
    private String serviceCategoryCode;

    /** 日程主键。 */
    private Long scheduleId;

    /** 执行地点主键。 */
    private Long locationId;

    /** 设备主键。 */
    private Long deviceId;

    /** 资源显示名称。 */
    private String resourceName;

    /** 是否启用。 */
    private Boolean activeFlag;
}
