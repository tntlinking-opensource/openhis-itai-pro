package com.openhis.whale.booking.application.medtech.response;

import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 医技资源映射分页响应（7.7）。
 */
@Getter
@Setter
public class PageDiagnosticResourceMapResponse extends PagedResult<DiagnosticResourceMapDto> {
}
