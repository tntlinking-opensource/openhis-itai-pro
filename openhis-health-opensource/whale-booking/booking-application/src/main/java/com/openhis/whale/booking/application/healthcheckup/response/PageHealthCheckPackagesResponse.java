package com.openhis.whale.booking.application.healthcheckup.response;

import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 体检套餐分页响应（9.1）。
 */
@Getter
@Setter
public class PageHealthCheckPackagesResponse extends PagedResult<HealthCheckPackageDto> {
}
