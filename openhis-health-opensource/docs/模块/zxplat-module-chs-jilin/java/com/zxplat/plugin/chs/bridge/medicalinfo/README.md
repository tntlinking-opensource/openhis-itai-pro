# medicalinfo bridge

医保信息查询（5201–5301）。

## 包结构

```text
medicalinfo/
  support/   MedicalInfoInvokeSupport（契约解析、签到上下文、入参校验）
  invoker/   Med5201–Med5301Invoker
```

## infno 索引

| infno | Invoker | 说明 |
|-------|---------|------|
| 5201 | `Med5201Invoker` | 就诊信息 |
| 5202 | `Med5202Invoker` | 诊断信息 |
| 5203 | `Med5203Invoker` | 结算信息 |
| 5204 | `Med5204Invoker` | 费用明细 |
| 5205 | `Med5205Invoker` | 慢特病用药记录 |
| 5206 | `Med5206Invoker` | 累计信息（亦供 benefit Extension） |
| 5301 | `Med5301Invoker` | 慢特病备案（亦供 benefit Extension） |

## 调用链

```text
InsuranceMedicalInfoQueryAppService (application)
  → Med5201QueryInvoker … Med5301QueryInvoker (domain access)
  → Med5201Invoker … Med5301Invoker (bridge)

InsuranceRegisterBenefitQueryExtensionImpl (benefit Extension)
  → Med5206QueryInvoker / Med5301QueryInvoker (domain access)
  → Med5206Invoker / Med5301Invoker (bridge)
```

脚本：`scripts/chs-p11-medicalinfo-invoker.ps1`
