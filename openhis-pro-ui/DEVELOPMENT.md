# 开发注意事项

## 📋 目录

- [环境要求](#环境要求)
- [项目初始化](#项目初始化)
- [代码规范](#代码规范)
- [Git 提交规范](#git-提交规范)
- [开发流程](#开发流程)
- [常见问题](#常见问题)

---

## 环境要求

### 必需环境

- **Node.js**: `>=20.12.0`
- **pnpm**: `>=10.0.0`
- **Git**: 最新版本

### 推荐编辑器

- **VSCode CURSOR** (推荐)
  - 安装插件：`Prettier - Code formatter`
  - 安装插件：`ESLint`
  - 安装插件：`Vue Language Features (Volar)`

### 编辑器配置

项目已配置 `.vscode/settings.json`，会自动：

- 保存时自动格式化（使用 Prettier）
- 使用项目内置的 Prettier 配置（带 Tailwind 插件）

---

## 项目初始化

### 1. 克隆项目

```bash
git clone <repository-url>
cd his-ui
```

### 2. 安装依赖

```bash
# 使用 pnpm（必须，项目已配置 only-allow pnpm）
pnpm install
```

### 3. 启动开发服务器

```bash
pnpm dev
```

### 4. 安装 Git Hooks

项目使用 `lefthook` 管理 Git Hooks，首次克隆后会自动安装。

如果未自动安装，手动执行：

```bash
pnpm lefthook install
```

---

## 代码规范

### 1. 代码风格

#### 缩进和换行

- **缩进**: 2 个空格（不是 Tab）
- **换行符**: LF（Unix 风格）
- **文件末尾**: 必须有换行符
- **最大行宽**: 100 字符

#### 引号

- **JavaScript/TypeScript**: 使用单引号 `'`
- **JSON**: 使用双引号 `"`

#### 示例

```typescript
// ✅ 正确（JS/TS 用单引号）
const name = 'John';
const obj = { name: 'John' };

// ❌ 错误（不符合项目引号规范）
const name = 'John';
const obj = { name: 'John' };
```

### 2. ESLint 规则

项目使用 `@zxplat/eslint-config`，主要规则：

- ✅ 使用 `===` 而不是 `==`
- ✅ 禁止使用 `console.log`（允许 `console.warn` 和 `console.error`）
- ✅ 禁止使用 `debugger`
- ✅ 未使用的变量会被警告

### 3. TypeScript 规范

- ✅ 所有文件必须通过类型检查
- ✅ 避免使用 `any`，优先使用具体类型
- ✅ 使用 `interface` 定义对象类型
- ✅ 使用 `type` 定义联合类型或工具类型

```typescript
// ✅ 正确
interface User {
  id: number;
  name: string;
}

// ❌ 错误
const user: any = { id: 1, name: 'John' };
```

### 4. Vue 组件规范

#### 组件结构顺序

```vue
<script setup lang="ts">
// 1. 导入
import { ref } from 'vue';

// 2. Props/Emits 定义
defineProps<{ name: string }>();

// 3. 响应式数据
const count = ref(0);

// 4. 计算属性
const doubled = computed(() => count.value * 2);

// 5. 方法
function increment() {
  count.value++;
}

// 6. 生命周期
onMounted(() => {
  // ...
});
</script>

<template>
  <!-- 模板内容 -->
</template>

<style scoped>
/* 样式 */
</style>
```

#### Props 默认值

```vue
<script setup lang="ts">
// ✅ 正确：使用 withDefaults
withDefaults(defineProps<{ name?: string }>(), {
  name: undefined,
});

// ❌ 错误：缺少默认值
defineProps<{ name?: string }>();
</script>
```

### 5. 日期处理

#### 日期格式

- **统一格式**: `YYYY-MM-DD HH:mm:ss`
- **使用 dayjs**: 项目使用 `dayjs` 处理日期

```typescript
import dayjs from 'dayjs';

// ✅ 正确
const formatted = dayjs(date).format('YYYY-MM-DD HH:mm:ss');

// 在 VxeTable 中使用 ElDatePicker
// 已配置自动格式化，无需手动处理
editRender: {
  name: 'ElDatePicker';
}
```

### 6. 数值计算

#### 使用 decimal.js

涉及金额、数量等精确计算时，**必须**使用 `decimal.js`：

```typescript
import { decimalAdd, decimalMul, decimalDiv, decimalSub } from '@/utils/decimal-util';

// ✅ 正确
const total = decimalMul(price, quantity);

// ❌ 错误（可能产生精度问题）
const total = price * quantity;
```

**默认精度**: 4 位小数，四舍五入

---

## Git 提交规范 （不必须）

### 1. 提交前检查

项目配置了 `lefthook`，在 `pre-commit` 时会自动：

1. ✅ 运行 `lint-staged`（ESLint + Prettier）
2. ✅ 格式化所有暂存文件
3. ✅ 自动修复可修复的问题
4. ✅ 如果修复了文件，会自动重新暂存

**注意**: 如果提交时出现错误，请修复后再提交。

### 2. 提交信息格式 （不必须）

项目使用 `czg`（commitizen）规范提交信息。

#### 使用方式

```bash
# 使用交互式提交（推荐）
pnpm commit

# 或直接使用 git commit（需要手动遵循规范）
git commit -m "feat: IDO1L0 添加新功能"
```

#### 提交类型

- `feat`:新功能
- `fix`: 修复 bug
- `docs`: 文档更新
- `style`: 代码格式调整（不影响功能）
- `refactor`: 代码重构
- `perf`: 性能优化
- `test`: 测试相关
- `chore`: 构建/工具链相关
- `revert`: 回滚提交

#### 提交信息示例

```bash
feat: IDO1L0 添加采购单详情页面
fix: IDO1L0  修复日期格式问题
docs: IDO1L0  更新开发文档
refactor: IDO1L0  重构表格组件
```

### 3. 提交前注意

- ✅ 确保所有测试通过
- ✅ 确保没有 `console.log`（允许 `console.warn` 和 `console.error`）

---

## 开发流程

### 1. 创建新功能 （不必须）

```bash
# 1. 创建功能分支
git checkout -b feat/编号

# 2. 开发代码
# ... 编写代码 ...

# 3. 提交代码
pnpm commit

# 4. 推送到远程
git push origin feat/编号
```

### 2. 代码检查

```bash
# 检查代码格式和 ESLint
pnpm lint

# 检查 TypeScript 类型
pnpm typecheck

# 检查所有（包括循环依赖、拼写等）
pnpm check
```

### 3. 代码格式化

```bash
# 格式化所有代码
pnpm format

# 或只格式化特定文件
pnpm prettier --write src/path/to/file.ts
```

### 4. 构建项目

```bash

pnpm build
```

---

## 常见问题

### 1. 提交时 Prettier 格式化失败

**问题**: 提交时提示 Prettier 格式化失败

**解决**:

```bash
# 手动格式化所有文件
pnpm format

# 或只格式化暂存文件
pnpm prettier --write $(git diff --cached --name-only)
```

### 2. ESLint 错误太多

**问题**: ESLint 报错太多，不知道如何修复

**解决**:

```bash
# 自动修复可修复的问题
pnpm lint --fix

# 如果还有错误，查看具体错误信息，手动修复
```

### 3. TypeScript 类型错误

**问题**: `pnpm typecheck` 报类型错误

**解决**:

- 检查导入路径是否正确
- 检查类型定义是否完整
- 使用 `as` 断言（尽量少用，优先修复类型定义）

### 4. Git Hooks 不生效

**问题**: 提交时没有自动运行 lint-staged

**解决**:

```bash
# 重新安装 lefthook
pnpm lefthook install

# 或手动运行
pnpm lefthook run pre-commit
```

### 5. 依赖安装失败

**问题**: `pnpm install` 失败

**解决**:

```bash
# 清理并重新安装
pnpm clean --del-lock
pnpm install

# 或使用项目提供的命令
pnpm reinstall
```

### 6. 日期格式问题

**问题**: 日期提交到后端格式不对

**解决**:

- 在 VxeTable 中使用 `editRender: { name: 'ElDatePicker' }`
- 已自动配置为 `YYYY-MM-DD HH:mm:ss` 格式
- 无需手动格式化

### 7. 数值计算精度问题

**问题**: 金额计算出现精度误差

**解决**:

- 使用 `decimal.js` 工具函数
- 导入: `import { decimalAdd, decimalMul, ... } from '@/utils/decimal-util'`
- 不要使用原生 `+`, `-`, `*`, `/` 进行金额计算

---

## 项目结构

```
his-ui/
├── apps/                    # 应用目录
│   └── web-ele/            # Element Plus 版本（当前主要开发）
├── packages/               # 共享包
│   ├── @core/              # 核心包
│   ├── effects/            # 效果包
│   └── ...
├── internal/               # 内部工具
│   ├── lint-configs/       # 代码规范配置
│   └── ...
└── scripts/                # 脚本目录
```

---

## 重要提示

1. ⚠️ **必须使用 pnpm**，不要使用 npm 或 yarn
2. ⚠️ **提交前必须通过 lint 检查**，否则会被拒绝
3. ⚠️ **不要提交 `console.log`**，使用 `console.warn` 或 `console.error`
4. ⚠️ **金额计算必须使用 decimal.js**，避免精度问题
5. ⚠️ **日期格式统一为 `YYYY-MM-DD HH:mm:ss`**
6. ⚠️ **原则上不允许使用 --no-verify**

---

## 获取帮助

- 查看 ESLint 配置: `eslint.config.mjs`
- 查看 Prettier 配置: `.prettierrc.mjs`
- 查看 EditorConfig: `.editorconfig`

---
