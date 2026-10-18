# benefit bridge

挂号待遇 / 读卡查询（1101、2001）。

## 包结构

```text
benefit/
  mapper/    Op1101ResultMapper、InsuranceBenefitIntegrationMapper 等
  invoker/   Op1101、Op2001Invoker
```

## infno 索引

| infno | Invoker | 说明 |
|-------|---------|------|
| 1101 | `Op1101Invoker` | 人员信息（亦供 application 读卡） |
| 2001 | `Op2001Invoker` | 待遇检查 |

5206、5301 已迁至 [medicalinfo](../medicalinfo/README.md) cap（P11）。

## 调用链（读卡）

```text
InsuranceRegisterBenefitQueryExtensionImpl
  → Op1101Invoker (invoker)
  → InsuranceBenefitIntegrationMapper (mapper)
```
