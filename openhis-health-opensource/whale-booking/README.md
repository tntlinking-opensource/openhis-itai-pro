# whale-booking

Whale 预约扩展包：门诊排班、号源、挂号、队列、体检/医技/床位预约及渠道对接。

---

## Maven 子模块

| 模块 | 职责 |
|------|------|
| `booking-contracts` | Port 契约（`BookingPatientPort` 等）+ 跨模块 DTO |
| `booking-domain` | 日程/号源/预约/策略/模板等领域实体与写读服务 |
| `booking-application` | AppService、Flyway、Support、Mapper |
| `booking-adapter` | HIS 主数据 Port 实现（`com.openhis.whale.booking.adapter`） |
| `booking-starter` | 安装入口（聚合 application + adapter） |

依赖方向：`application` → `domain` → `contracts`；`adapter` → `contracts` + `health-domain`（**禁止**依赖 `booking-application`）。

**双向 Port 例外**：`health-application` 可编译依赖 `booking-contracts`；`booking-adapter` 读 HIS 主数据。

---

## 装配

- 开关：`openhis.modules.booking.enabled=true`
- Application：扫描 `com.openhis.whale.booking.application`
- Adapter：`BookingAdapterAutoConfiguration` → 扫描 `com.openhis.whale.booking.adapter`
- 模块元数据：`BookingAdapterModuleDescriptor`（moduleId=`booking`）
- Maven Profile：`-Pwith-booking`（与 report/print 独立）

---

## 应用层：按业务场景分包

| 场景 | AppService | 说明 |
|------|------------|------|
| 门诊排班 | `OutpatientScheduleAppService` | 日程 CRUD、放号、停诊、替诊 |
| 号源管理 | `SlotManageAppService` | 号源列表、封锁、删除 |
| 挂号窗口 | `BookingRegisterAppService` | 预约、改约、取消、候补 |
| 策略 | `BookingPolicyAppService` | 放号/挂号/停诊变更策略 |
| 模板 | `ScheduleTemplateAppService` | T1/T2/TW 排班模板 |
| 日历例外 | `CalendarExceptionAppService` | 节假日/调休 |
| 队列 | `QueueBoardAppService` | 报到、叫号、过号 |
| 渠道 | `ChannelBookingAppService` | 外部渠道预约 |
| 体检 | `HealthCheckupBookingAppService` | 团检/个检 |
| 医技 | `MedTechBookingAppService` | 医技预约 |
| 床位 | `BedBookingAppService` | 床位预留/入院 |

共享：`BookingOperatorResolver`（操作人）、`BookingPagedResultBuilder`（分页）。

---

## JSON 列契约（与 print/report 设计器同类）

| 方向 | 形态 | 说明 |
|------|------|------|
| **入参** / 写库 | `String` JSON | 如 `SaveReleasePolicyRequest.channelQuotaJson`、`slotTemplatesJson` |
| API **出参** | 结构化 DTO / `Map` | 如 `ReleasePolicyDto.channelQuotas`；禁止 Response 整段 `*Json` |
| 实体列 | `ReleasePolicy.channelQuotaJson` 等 | 库表真源 |

解析 Support：`ReleasePolicyJsonSupport`、`TemplateJsonSupport`、`ReportDesignJsonSupport`（report 模块）同类模式。

---

## 规范要点（对齐 `代码生成通用规范`）

- AppService 禁止直注 `ICurrentUser` → 经 `BookingOperatorResolver`
- Repository 禁止 `@Query` → Specification 或派生方法名
- 禁止三元运算符；常量见 `domain.share.*Constants`
- Support 单测：`ReleasePolicyJsonSupportTest`、`TemplateJsonSupportTest` 等

文档真源：`whale-doc/booking/03-详细设计与编码指导.md`。
