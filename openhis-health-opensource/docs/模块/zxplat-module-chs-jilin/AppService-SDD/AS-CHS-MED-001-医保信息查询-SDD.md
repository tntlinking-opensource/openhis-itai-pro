# 医保信息查询 · 应用服务详细设计（SDD）

> 编码：**AS-CHS-MED-001-SDD** · v0.2 · 2026-07-11  
> **应用服务编号**：AS-CHS-MED-001

## 1. 边界

| 项 | 内容 |
| --- | --- |
| AppService | `InsuranceMedicalInfoQueryAppService` |
| HTTP | `/api/app/insuranceMedicalInfoQuery` |
| infno | 5201–5206、5301 |

## 2. 包结构

```text
medicalinfo/
  service/InsuranceMedicalInfoQueryAppService.java   # 薄入口，直调 Invoker
  invoker/Med5201Invoker … Med5205Invoker
  support/MedPlatformSupport
  # 5206/5301：domain Med5206QueryInvoker / Med5301QueryInvoker
```

**无强制 Workflow 层**：同页多 query，AppService 直接注入 Invoker（对齐 health）。

## 3. HTTP ↔ IF

| 方法 | infno |
| --- | --- |
| `queryMdtrt5201` | 5201 |
| `queryDise5202` | 5202 |
| `querySetl5203` | 5203 |
| `queryFee5204` | 5204 |
| `queryOpspDrug5205` | 5205 |
| `queryCum5206` | 5206 |
| `queryOpspFiling5301` | 5301 |

## 4. 事务

全部只读；`AppServiceAspect` 只读传播。
