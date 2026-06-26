/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inpatient.facade;

import com.openhis.chs.domain.inpatient.dto.IpAdmissionResult;
import com.openhis.chs.domain.inpatient.dto.IpConfirmSettleCommand;
import com.openhis.chs.domain.inpatient.dto.IpConfirmSettleResult;
import com.openhis.chs.domain.inpatient.dto.IpEncounterPlatformCommand;
import com.openhis.chs.domain.inpatient.dto.IpPreSettleResult;
import com.openhis.chs.domain.inpatient.dto.IpRevokeFeeDetailCommand;
import com.openhis.chs.domain.inpatient.dto.IpRevokeFeeDetailResult;
import com.openhis.chs.domain.inpatient.dto.IpRevokeSettleCommand;
import com.openhis.chs.domain.inpatient.dto.IpRevokeSettleResult;
import com.openhis.chs.domain.inpatient.dto.IpSettleContext;

/** 住院医保结算领域操作（2401→2301→2402→2303→2304；Wave 2 撤销/变更）。 */
public interface ChsInpatientSettleOperations {

    IpAdmissionResult ensureAdmission(IpSettleContext context);

    IpPreSettleResult preSettle(IpSettleContext context);

    IpConfirmSettleResult confirmSettle(IpConfirmSettleCommand command);

    IpRevokeSettleResult revokeSettle(IpRevokeSettleCommand command);

    IpRevokeFeeDetailResult revokeFeeDetails(IpRevokeFeeDetailCommand command);

    void revokeAdmission(IpEncounterPlatformCommand command);

    void revokeDischarge(IpEncounterPlatformCommand command);

    void changeInpatientInfo(IpSettleContext context);
}
