package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存团检单位请求（9.3）。
 */
@Getter
@Setter
public class SaveGroupCheckupOrgRequest {

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

    /** 合同年度。 */
    private String contractYear;
}
