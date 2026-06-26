package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 保存体检套餐请求（9.1）。
 */
@Getter
@Setter
public class SaveHealthCheckPackageRequest {

    /** 体检套餐主键（更新时传入）。 */
    private Long id;

    /** 机构/科室主键。 */
    private Long organizationId;

    /** 套餐编码。 */
    private String packageCode;

    /** 套餐名称。 */
    @NotBlank
    private String packageName;

    /** 适用性别。 */
    private String applicableGender;

    /** 适用年龄下限。 */
    private Integer applicableAgeMin;

    /** 适用年龄上限。 */
    private Integer applicableAgeMax;

    /** 预估金额。 */
    private BigDecimal estimatedAmount;

    /** 是否启用。 */
    private Boolean activeFlag;

    /** 套餐项目列表。 */
    private List<HealthCheckPackageItemRequest> items = new ArrayList<>();
}
