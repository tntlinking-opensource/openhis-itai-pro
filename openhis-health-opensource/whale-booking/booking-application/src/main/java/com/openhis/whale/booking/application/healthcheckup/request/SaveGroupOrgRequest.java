package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存团检单位请求（6.x 旧版 API 别名，对应 SaveGroupCheckupOrgRequest）。
 */
@Getter
@Setter
public class SaveGroupOrgRequest {

    /** 团检单位主键（更新时传入）。 */
    private Long id;

    /** 单位编码。 */
    private String orgCode;

    /** 单位名称。 */
    @NotBlank
    private String orgName;

    /** 联系人姓名。 */
    private String contactName;

    /** 联系电话。 */
    private String contactPhone;

    /** 结算方式。 */
    private String settlementMode;
}
