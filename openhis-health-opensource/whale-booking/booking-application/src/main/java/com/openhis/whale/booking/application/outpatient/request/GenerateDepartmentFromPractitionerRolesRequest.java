package com.openhis.whale.booking.application.outpatient.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 科室级岗位批量生成 Schedule 草稿（2.22）。
 */
@Getter
@Setter
public class GenerateDepartmentFromPractitionerRolesRequest {

    @NotNull
    private Long organizationId;

    /** 指定医师；空=科室全部在岗医师。 */
    private List<Long> practitionerIds;
}
