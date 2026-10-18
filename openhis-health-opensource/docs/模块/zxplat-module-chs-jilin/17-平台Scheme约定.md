# 17 — 平台 Scheme 约定

> **适用范围**：`chs-jilin-application` 内所有 `*PlatformAssembler`、Output Parser 与平台报文 DTO。  
> **目标**：以强类型 DTO 替代 `JSONObject.put`，对齐工伤 `Wi*Input/Output` 模式，便于调试与单测。

## 目录结构

```
chs-jilin-gateway/.../scheme/          # v3 真源落点（旧文 api/scheme 已废）
├── common/           # 公共 Row / JsonSupport
├── inpatient/        # Ip2401Input …
├── outpatient/       # Op2201Input …
├── benefit/          # Op1101 / Op2001 …
├── reconciliation/   # Recon3201Input …
├── medicalinfo/      # Med5201Input …
├── inventory/        # Inv3501Input …
├── catalog/          # Cat3301Input、Dict1901Input …
├── audit/            # Audit3101/3102/3103（📄 见 25-明细审核31xx-详细设计；待编码）
└── … 
```

> **历史**：曾写在 `chs-jilin-application/.../api/scheme/`；v3 以 **gateway/scheme** 为准。

**Domain 边界**：`PlatformSetlinfoFields`、`ChsFeeUploadResultRow` 等仅含领域读写字段，位于 `chs-jilin-domain/.../platform/dto/`。Facade 通过 `ChsSetlinfoMapper` 与 application scheme 互转。

## 命名

| 类型 | 规则 | 示例 |
|------|------|------|
| Input | `{Domain}{Infno}Input` | `Ip2401Input`、`Audit3101Input` |
| Output | `{Domain}{Infno}Output` | `ChsSetlinfoOutput`、`Audit3101Output` |
| 行明细 | 语义名 + `Row` / `Line` | `ChsFeeDetailLine`、`Audit3101OrderRow` |
| 前缀 | `Op`/`Ip`/`Med`/`Recon`/`Cat`/`Inv`/`Dict`/**`Audit`**（31xx） | `Audit3103WarnRow` |
| 字段 | Java camelCase + `@JSONField(name="snake_case")` | `mdtrtId` → `mdtrt_id` |

## 字段范围

- **只建模当前代码实际发送/读取的字段**，不全量照抄合订本。
- 现场新增字段：先改 Assembler，再补 DTO 属性。
- **5201–5301 查询 output 列表**仍用 `MedicalInfoQueryResult` Map 行（省际 key 差异），Input 必须 scheme 化。

## Assembler 规范

- 禁止在 `facadeimpl/**/assembler/` 使用 `new JSONObject()`。
- 返回强类型 DTO，直接传入 `gatewayClient.invoke(..., input, ...)`。
- Lombok 链式：`ChsDataInput` 子类需分步 `new` + `setData()`，避免链式返回父类型。

## 测试

- 新增/变更 infno：补充 golden JSON 单测（`ChsSchemeJsonSupport.toJsonObject(dto)` 与迁移前报文树等价）。
- 公共工具：`ChsSchemeJsonSupportTest`。

## P0 自检

```bash
rg "new JSONObject\(\)" zxplat-module-chs-jilin/chs-jilin-application/src/main/java/com/openhis/chs/application/facadeimpl -g "**/assembler/**"
# 期望 0 命中
```
