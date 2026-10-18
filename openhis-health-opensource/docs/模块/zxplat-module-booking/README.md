# ZXPlat Booking 文档

> **统一预约**扩展 Module（v3）。集成：**Extension + bridge Orchestrator**。  
> **文档目录**：`docs/模块/zxplat-module-booking/`（与代码工程同名）  
> **架构真源**：[17-zxplat-module-booking-架构-v3.md](./17-zxplat-module-booking-架构-v3.md) · 代码 `zxplat-module-booking`

## 阅读顺序

| 序号 | 文档 | 说明 |
|------|------|------|
| **17** | [17-zxplat-module-booking-架构-v3.md](./17-zxplat-module-booking-架构-v3.md) | **架构 v3** |
| **13** | [13-实现状态与文档对照.md](./13-实现状态与文档对照.md) | 实现对照 |
| **00** | [00-功能清单.md](./00-功能清单.md) | 功能范围 |
| **01** | [01-概要设计.md](./01-概要设计.md) | v2 HLD（包名以 17 为准） |
| **02** | [02-交互说明.md](./02-交互说明.md) | UI |
| **03** | [03-详细设计与编码指导.md](./03-详细设计与编码指导.md) | LLD / 成码 |
| **18** | [18-体检号源日历与预约-需求定稿.md](./18-体检号源日历与预约-需求定稿.md) | **H 线改造需求真源**（号源日历 + 合同类型分池 + 窗口/小程序占号） |
| — | [模块说明-代码结构.md](./模块说明-代码结构.md) | Maven + Java 包 |

```yaml
zxplat.modules.booking.enabled: true
```

Profile：`-Pwith-booking` → `booking-starter`
