# DM-BKG-004 HealthCheckup · DomainModel-SDD

> **对应 DM**：`../DomainModel/healthcheckup/DM-BKG-004-HealthCheckup-体检号源.md`  
> **包**：`com.zxplat.plugin.booking.domain.healthcheckup` · **模式**：B · **日期**：2026-09-24

## 1. 边界

在内：日计划/时段/模板/套餐/预约扩展；占号 Port。在外：团体批次 HTTP 编排、HIS 患者。

## 2. 服务落点

| 类 | 职责 |
| --- | --- |
| `HealthCheckupSlotCalendarDomainService` | 模板/生成未来日/休班/容量/复制 |
| `HealthCheckupDomainService` | 预约扩展写 |
| `*QueryService` | 月览/日详/可约 |
| `HealthCheckupSlotOccupyPort` | 占号端口（application 实现） |

## 3–8

口径同插件统规；产品细则 `18-体检号源日历与预约-需求定稿.md`；无待跟踪缺口。
