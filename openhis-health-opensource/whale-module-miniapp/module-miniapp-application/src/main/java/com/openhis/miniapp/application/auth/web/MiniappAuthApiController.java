/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.miniapp.application.auth.web;

import com.openhis.miniapp.application.auth.request.MiniappAutoLoginRequest;
import com.openhis.miniapp.application.auth.request.MiniappBindCampusStaffRequest;
import com.openhis.miniapp.application.auth.request.MiniappBindCampusStudentRequest;
import com.openhis.miniapp.application.auth.request.MiniappBindPatientRequest;
import com.openhis.miniapp.application.auth.request.MiniappBindPractitionerRequest;
import com.openhis.miniapp.application.auth.request.MiniappDevLoginRequest;
import com.openhis.miniapp.application.auth.request.MiniappSwitchPatientRequest;
import com.openhis.miniapp.application.auth.request.MiniappSwitchPractitionerRequest;
import com.openhis.miniapp.application.auth.response.MiniappLoginResponse;
import com.openhis.miniapp.application.auth.response.MiniappSessionResponse;
import com.openhis.miniapp.application.auth.service.MiniappAuthAppService;
import com.openhis.whale.core.application.dto.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序身份 HTTP 门面（{@code /api/miniapp/miniappAuth/*}）。
 */
@RestController
@RequestMapping("/api/miniapp/miniappAuth")
@RequiredArgsConstructor
public class MiniappAuthApiController {

    private final MiniappAuthAppService miniappAuthAppService;

    @PostMapping("/autoLogin")
    public Result<MiniappLoginResponse> autoLogin(@RequestBody MiniappAutoLoginRequest request) {
        return Result.success(miniappAuthAppService.autoLogin(request));
    }

    @PostMapping("/devLogin")
    public Result<MiniappLoginResponse> devLogin(@RequestBody MiniappDevLoginRequest request) {
        return Result.success(miniappAuthAppService.devLogin(request));
    }

    @PostMapping("/currentSession")
    public Result<MiniappSessionResponse> currentSession() {
        return Result.success(miniappAuthAppService.currentSession());
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        miniappAuthAppService.logout();
        return Result.success();
    }

    @PostMapping("/bindPatient")
    public Result<MiniappSessionResponse> bindPatient(@RequestBody MiniappBindPatientRequest request) {
        return Result.success(miniappAuthAppService.bindPatient(request));
    }

    @PostMapping("/bindCampusStudent")
    public Result<MiniappSessionResponse> bindCampusStudent(
            @RequestBody MiniappBindCampusStudentRequest request) {
        return Result.success(miniappAuthAppService.bindCampusStudent(request));
    }

    @PostMapping("/bindCampusStaff")
    public Result<MiniappSessionResponse> bindCampusStaff(
            @RequestBody MiniappBindCampusStaffRequest request) {
        return Result.success(miniappAuthAppService.bindCampusStaff(request));
    }

    @PostMapping("/switchPatient")
    public Result<MiniappSessionResponse> switchPatient(@RequestBody MiniappSwitchPatientRequest request) {
        return Result.success(miniappAuthAppService.switchPatient(request));
    }

    @PostMapping("/bindPractitioner")
    public Result<MiniappSessionResponse> bindPractitioner(
            @RequestBody MiniappBindPractitionerRequest request) {
        return Result.success(miniappAuthAppService.bindPractitioner(request));
    }

    @PostMapping("/switchPractitioner")
    public Result<MiniappSessionResponse> switchPractitioner(
            @RequestBody MiniappSwitchPractitionerRequest request) {
        return Result.success(miniappAuthAppService.switchPractitioner(request));
    }
}
