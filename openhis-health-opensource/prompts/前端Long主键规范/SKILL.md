---
name: zxplat-long-id-frontend
description: >-
  防止 OpenHIS/Whale 前端对后端 Long/Snowflake 主键做 Number 转换导致精度丢失。
  触发：organizationId/practitionerId/patientId 等 ID、OptionDto 下拉、
  库里有数据但接口/practitioners 为空、Network 中 ID 末位被抹平（如 ...917200 vs ...917184）、
  Number(item.value)、booking/health 前端 ID 传参。
---

# 前端 Long 主键（Snowflake ID）规范

## 问题（必读）

后端 Java `Long` 主键常为 18 位 Snowflake ID，**超过** `Number.MAX_SAFE_INTEGER`（`9007199254740991`）。

```javascript
Number("322663218269917184") // → 322663218269917200 ❌
```

后果：

- API 请求 `organizationId` 错位 → 后端查不到岗位/主数据 → **下拉为空**
- `ElSelect` 选中值与 `OptionDto.value` 对不上
- 表现为「库里有数据、接口返回空数组」，易误判为后端 bug

**真实案例（booking 门诊排班）**：传 `322663218269917200`，库中科室为 `322663218269917184` → `practitioners: []`。

## 硬性规则

1. **实体主键在 TS 中用 `string`**，类型名如 `BookingEntityId` / `EntityId`。
2. **禁止**对 ID 使用：`Number(id)`、`parseInt(id)`、`+id`、一元加号。
3. **禁止** JSON 请求体以 number 发送 Long ID；axios 应传 **string**（Jackson 可反序列化为 Long）。
4. **`OptionDto.value` 已是 string**（对齐后端 `OptionDto`）；`ElOption :value` 直接用 `item.value` 或 `bookingOptionValue(item.value)`。
5. **比较 ID** 用字符串相等：`String(a) === String(b)` 或 `isSameBookingId(a, b)`，不用 `===` 跨 number/string。
6. **`v-model` 绑 ID 的 ref 类型为 `string | undefined`**，不是 `number`。

## 项目内现成工具（booking）

优先复用，不要重复造轮子：

| 文件 | 用途 |
|------|------|
| `zxplat-ui/apps/web-ele/src/api/booking/booking-id.ts` | `BookingEntityId`、`toBookingId`、`isSameBookingId`、`bookingOptionValue` |
| `zxplat-ui/apps/web-ele/src/views/booking/composables/use-booking-organization.ts` | 登录科室 ID 以 string 返回 |

booking 模块 API 类型中的 `organizationId`、`practitionerId` 等应使用 `BookingEntityId`（string）。

非 booking 模块新增代码时：复制 `booking-id.ts` 到 `src/utils/entity-id.ts` 并统一引用，或抽公共包（改前先搜是否已有）。

## 编码检查清单

- [ ] 所有 `*Id` 字段（organization / practitioner / patient / schedule / slot / encounter / appointment）为 `string`
- [ ] 无 `Number(item.value)` 用于下拉 option value
- [ ] composable 不从 `getSelectedOrganization()` 做 `Number()` 转换
- [ ] API interface 中 Long 字段不是 `number`
- [ ] Network 中 ID 为 JSON **字符串** `"322663218269917184"`，而非裸数字

## 排查「下拉为空」顺序

1. Network 请求体：ID 末几位是否被「抹平」→ **精度问题**
2. 用正确 string ID 重放请求 → 有数据则确认是前端传参
3. 再查库/岗位/主数据（后端逻辑）

## 正确 / 错误示例

```typescript
import { toBookingId, bookingOptionValue, type BookingEntityId } from '#/api/booking/booking-id';

const organizationId = toBookingId(authStore.getSelectedOrganization());
await loadScheduleFormInitApi({ organizationId });

<ElOption :value="bookingOptionValue(item.value)" />
```

```typescript
const id = Number(raw);              // ❌
organizationId: Number(item.value)   // ❌
const orgId = +selectedOrganizationId; // ❌
```

## 后端对齐

- 后端 `OptionDto.value` 为 **String**（`zxplat-framework` `OptionDto`）。
- 响应 JSON 中 `value` 已是字符串时，前端不要再 Number 化。
- 若某接口仍返回 number 类型的 Long，收到后 **立即 `String(id)` 存档**，勿经 Number。

## 适用范围

`zxplat-ui` · booking / health 挂号 / 医生站 / 护士站；任何消费 `OptionDto`、Snowflake 主键的下拉、路由参数、localStorage 科室 ID。
