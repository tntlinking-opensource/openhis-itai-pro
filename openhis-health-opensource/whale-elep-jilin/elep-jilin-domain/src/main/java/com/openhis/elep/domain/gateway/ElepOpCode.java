/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.domain.gateway;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 吉林省电子处方中台 OpCode。 */
@Getter
@RequiredArgsConstructor
public enum ElepOpCode {

    A0001("A0001", "/fixmedins/uploadChk"),
    A0002("A0002", "/fixmedins/rxFixmedinsSign"),
    A0003("A0003", "/fixmedins/rxFileUpld"),
    A0004("A0004", "/fixmedins/rxUndo"),
    A0005("A0005", "/fixmedins/hospRxDetlQuery"),
    A0006("A0006", "/fixmedins/rxSetlInfoQuery");

    private final String code;
    private final String defaultPath;
}
