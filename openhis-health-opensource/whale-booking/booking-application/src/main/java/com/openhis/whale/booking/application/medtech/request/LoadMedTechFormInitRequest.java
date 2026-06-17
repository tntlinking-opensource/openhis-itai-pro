package com.openhis.whale.booking.application.medtech.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 医技预约台初始化请求（7.1）。
 */
@Getter
@Setter
public class LoadMedTechFormInitRequest {

    /** 机构/科室主键。 */
    private Long organizationId;
}
