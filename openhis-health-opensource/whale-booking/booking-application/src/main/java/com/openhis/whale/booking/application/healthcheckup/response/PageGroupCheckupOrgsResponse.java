package com.openhis.whale.booking.application.healthcheckup.response;

import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 团检单位分页响应（9.3）。
 */
@Getter
@Setter
public class PageGroupCheckupOrgsResponse extends PagedResult<GroupCheckupOrgDto> {
}
