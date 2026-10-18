# outpatient bridge

门诊医保结算 Extension 出站（2201–2208）。

## 包结构

```text
outpatient/
  context/     ChsOutpatientInsuranceContextLoader（实现 domain.loader.OpPreSettleContextLoader）
  mapper/      SetlinfoIntegrationMapper
  assembler/   OutpatientSettleCommandAssembler、InsuranceDiagnosisCodeAssembler 等
  invoker/     Op2201–Op2208Invoker（唯一调 gateway）
  workflow/    OpOutpatientPreSettleOrchestrator、OpPlatformRegistrationOrchestrator
```

## infno 索引

| infno | Invoker | Workflow |
|-------|---------|----------|
| 2201 | `Op2201Invoker` | `OpOutpatientPreSettleOrchestrator`、`OpPlatformRegistrationOrchestrator` |
| 2202 | `Op2202Invoker` | `OpPlatformRegistrationOrchestrator` |
| 2203 | `Op2203Invoker` | `OpOutpatientPreSettleOrchestrator` |
| 2204 | `Op2204Invoker` | `OpOutpatientPreSettleOrchestrator` |
| 2205 | `Op2205Invoker` | `OpOutpatientPreSettleOrchestrator` |
| 2206 | `Op2206Invoker` | `OpOutpatientPreSettleOrchestrator` |
| 2207 | `Op2207Invoker` | — |
| 2208 | `Op2208Invoker` | — |

## 调用链示例（预结算）

```text
OutpatientChargeInsuranceExtensionImpl
  → OpPreSettleContextLoader (domain.loader)
  → ChsOutpatientInsuranceContextLoader (context)
  → OpOutpatientPreSettleOrchestrator (workflow)
      → Op2201Invoker（缺 mdtrtId 时）
      → Op2203/2204/2205/2206Invoker
```
