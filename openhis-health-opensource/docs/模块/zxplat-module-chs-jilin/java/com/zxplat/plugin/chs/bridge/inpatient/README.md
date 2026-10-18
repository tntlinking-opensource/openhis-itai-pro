# inpatient bridge

住院医保结算与入院 Extension 出站编排（2301–2305、2401–2405）。

## 包结构

```text
inpatient/
  context/     ChsInpatientInsuranceContextLoader（实现 domain.loader.IpSettleContextLoader）
  assembler/   InpatientSettleCommandAssembler
  invoker/     Ip2301–2305、Ip2401–2405Invoker
  support/     IpPlatformReadmitForSettleSupport、IpPlatformDischargeRevokeSupport
  workflow/    IpInpatientPreSettleOrchestrator、IpAdmissionOrchestrator 等
```

## infno 索引

| infno | Invoker | Workflow / Support |
|-------|---------|-------------------|
| 2301 | `Ip2301Invoker` | `IpInpatientPreSettleOrchestrator`；重新入院重传见 `IpPlatformReadmitForSettleSupport` |
| 2302 | `Ip2302Invoker` | — |
| 2303 | `Ip2303Invoker` | `IpInpatientPreSettleOrchestrator` |
| 2304 | `Ip2304Invoker` | 确认前经 `IpPlatformDischargeRevokeSupport#ensureDischargedForSettle`（2402） |
| 2305 | `Ip2305Invoker` | — |
| 2401 | `Ip2401Invoker` | `IpAdmissionOrchestrator`、`IpInpatientPreSettleOrchestrator`；失效恢复见 `IpPlatformReadmitForSettleSupport` |
| 2402 | `Ip2402Invoker` | **正式结算确认前**（`Ip2304Invoker` / `IpPlatformDischargeRevokeSupport`）；**预结算不调** |
| 2403 | `Ip2403Invoker` | — |
| 2404 | `Ip2404Invoker` | 重新入院腾位 / 清同人多余在院（`IpPlatformReadmitForSettleSupport`、`IpPlatformDischargeRevokeSupport`） |
| 2405 | `Ip2405Invoker` | 出院撤销（Wave 2） |

## 调用链示例

**预结算**（不调 2402；平台 `mid_setl_flag` 恒 0）：

```text
InpatientChargeInsuranceExtensionImpl
  → IpSettleContextLoader (domain.loader)
  → ChsInpatientInsuranceContextLoader (context)
  → IpInpatientPreSettleOrchestrator (workflow)
      → Ip2401Invoker（缺入院登记时）
      → Ip2301Invoker → Ip2303Invoker
      →（mdtrt 失效）IpPlatformReadmitForSettleSupport#readmitAndReupload → 再 2303
```

**正式结算确认**（出院全结）：

```text
Ip2304Invoker
  → IpPlatformDischargeRevokeSupport#ensureDischargedForSettle（2402；已离院幂等）
  →（失败/死锁）IpPlatformReadmitForSettleSupport#readmitAndRefreshPreSettle → 再 2402
  → 2304（仍在院则补 2402 再 2304）
```
