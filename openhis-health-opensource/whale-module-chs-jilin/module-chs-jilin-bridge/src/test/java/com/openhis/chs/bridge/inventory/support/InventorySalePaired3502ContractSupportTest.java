/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.share.enums.ContractCategory;
import org.junit.jupiter.api.Test;

/** {@link InventorySalePaired3502ContractSupport} 工伤判定单测。 */
class InventorySalePaired3502ContractSupportTest {

    @Test
    void workInjuryCategoryDetected() {
        Contract c = new Contract();
        c.setCategoryEnum(ContractCategory.WORKER_INSURANCE.getCode());
        assertThat(InventorySalePaired3502ContractSupport.isWorkInjuryContract(c)).isTrue();
    }

    @Test
    void municipalNotWorkInjury() {
        Contract c = new Contract();
        c.setCategoryEnum(ContractCategory.MUNICIPAL_INSURANCE.getCode());
        assertThat(InventorySalePaired3502ContractSupport.isWorkInjuryContract(c)).isFalse();
    }
}
