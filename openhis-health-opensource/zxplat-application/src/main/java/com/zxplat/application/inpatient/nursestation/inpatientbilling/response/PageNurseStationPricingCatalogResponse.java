/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.inpatientbilling.response;

import com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingCatalogRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;

/**
 * IF-IP-PRC-01：划价选品目录分页响应（B.10.11）。
 *
 * <p>行类型 {@link NurseStationPricingCatalogRowDto}：含定价、单位槽、套餐子项、吸氧/心电 Gate 标记。</p>
 */
public class PageNurseStationPricingCatalogResponse extends PagedResult<NurseStationPricingCatalogRowDto> {
}
