package com.openhis.chs.domain.catalog.facade;

import com.openhis.chs.domain.catalog.dto.request.SignInRequest;

/**
 * 医保会话（9001）应用能力。
 */
public interface ChsSessionOperations {

    String signIn(SignInRequest request);
}
