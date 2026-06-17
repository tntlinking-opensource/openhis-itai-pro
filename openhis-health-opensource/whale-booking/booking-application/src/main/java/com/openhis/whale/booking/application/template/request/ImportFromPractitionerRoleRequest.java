package com.openhis.whale.booking.application.template.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 从岗位导入 T2 日程模板（2.24）。
 */
@Getter
@Setter
public class ImportFromPractitionerRoleRequest {

    @NotNull
    private Long organizationId;

    @NotNull
    private Long practitionerId;

    /** 模板编码；空则 PR-{practitionerId}。 */
    private String templateCode;

    /** 模板名称；空则「岗位导入-{practitionerId}」。 */
    private String templateName;
}
