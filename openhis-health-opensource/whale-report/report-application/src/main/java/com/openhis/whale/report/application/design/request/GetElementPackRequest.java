package com.openhis.whale.report.application.design.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GetElementPackRequest {

    private String packCode;
    /** flow | overlay；空则返回全部 */
    private String layoutMode;
}
