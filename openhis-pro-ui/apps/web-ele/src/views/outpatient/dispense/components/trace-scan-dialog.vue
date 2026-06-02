<script setup lang="ts">
import type { DispensePrescriptionItemRow } from './types';

import { nextTick, ref, watch } from 'vue';

import { ElButton, ElInput, ElMessage, ElTable, ElTableColumn, ElTag } from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';

import { resolveTraceCodeMock, TRACE_CODE_LENGTH } from './resolve-trace-code-mock';

defineOptions({
  name: 'DispenseTraceScanDialog',
});

const props = withDefaults(
  defineProps<{
    /** 当前待发药明细（扁平行，不含间隔行） */
    lines: DispensePrescriptionItemRow[];
    patientName?: string;
  }>(),
  {
    patientName: '',
  },
);

const visible = defineModel<boolean>({ default: false });
const traceByLineId = defineModel<Record<string, string>>('traceByLineId', { required: true });

/** 打开弹窗时的追溯码快照，取消时还原 */
const snapshotTrace = ref<Record<string, string>>({});

const scanInput = ref('');
const scanInputRef = ref<InstanceType<typeof ElInput> | null>(null);

type TraceScanLogStatus = 'duplicate' | 'invalid' | 'not_found' | 'ok';

interface TraceScanLogItem {
  id: string;
  code: string;
  message: string;
  status: TraceScanLogStatus;
  timeLabel: string;
}

const scanLog = ref<TraceScanLogItem[]>([]);

/** 追溯码是否视为未绑定（占位符） */
function isTraceSlotEmpty(value: string | undefined): boolean {
  const t = (value ?? '').trim();
  return t === '' || t === '—';
}

/** 某行当前展示的追溯码（弹窗内编辑结果） */
function effectiveTrace(row: DispensePrescriptionItemRow): string {
  const v = traceByLineId.value[row.id];
  if (v !== undefined && v.trim() !== '') return v.trim();
  return (row.traceCode ?? '').trim();
}

/** 弹窗打开时拉快照、清空日志与输入 */
watch(visible, (open) => {
  if (!open) return;
  snapshotTrace.value = { ...traceByLineId.value };
  scanLog.value = [];
  scanInput.value = '';
});

/** 弹窗动画结束后再聚焦，避免焦点落在遮罩上 */
function handleDialogOpened(): void {
  void nextTick(() => {
    setTimeout(() => {
      scanInputRef.value?.focus?.();
    }, 30);
  });
}

/** 将输入按换行拆分，并支持无换行时连续 20 位一段 */
function extractTraceCodes(raw: string): { bad: string[]; ok: string[] } {
  const normalized = raw.replaceAll('\r\n', '\n').replaceAll('\r', '\n');
  const segments = normalized.split('\n');
  const ok: string[] = [];
  const bad: string[] = [];
  for (const seg of segments) {
    const t = seg.trim();
    if (!t) continue;
    if (t.length === TRACE_CODE_LENGTH) {
      ok.push(t);
      continue;
    }
    if (t.length > TRACE_CODE_LENGTH && t.length % TRACE_CODE_LENGTH === 0) {
      for (let i = 0; i < t.length; i += TRACE_CODE_LENGTH) {
        ok.push(t.slice(i, i + TRACE_CODE_LENGTH));
      }
      continue;
    }
    bad.push(t.length > 40 ? `${t.slice(0, 40)}…` : t);
  }
  return { ok, bad };
}

/** 统计已绑定条数 */
function boundLineCount(): number {
  return props.lines.filter((row) => !isTraceSlotEmpty(effectiveTrace(row))).length;
}

/** 是否已全部绑定 */
function isAllBound(): boolean {
  return props.lines.length > 0 && boundLineCount() === props.lines.length;
}

type BindResult = 'bound' | 'duplicate' | 'invalid' | 'not_found';

/**
 * 单条追溯码：先走与后端同构的解析（当前为 resolveTraceCodeMock），再写入对应明细行。
 */
function tryBindSingleCode(code: string): BindResult {
  const trimmed = code.trim();
  if (trimmed.length !== TRACE_CODE_LENGTH) {
    pushLog(trimmed || '(空)', 'invalid', `须为 ${TRACE_CODE_LENGTH} 位`);
    return 'invalid';
  }

  const resolved = resolveTraceCodeMock(trimmed, props.lines);
  if ('reason' in resolved) {
    if (resolved.reason === 'NOT_FOUND') {
      pushLog(trimmed, 'not_found', '库中未找到该追溯码');
      ElMessage.warning('未找到该追溯码');
      return 'not_found';
    }
    pushLog(trimmed, 'invalid', `须为 ${TRACE_CODE_LENGTH} 位`);
    return 'invalid';
  }

  const { drugId, lineId, traceCode } = resolved;
  const row = props.lines.find((r) => r.id === lineId);
  if (!row) {
    pushLog(trimmed, 'not_found', '解析结果与当前待发列表不匹配');
    ElMessage.error('解析结果与当前列表不匹配');
    return 'not_found';
  }

  const boundElsewhere = props.lines.some(
    (r) =>
      r.id !== lineId && !isTraceSlotEmpty(effectiveTrace(r)) && effectiveTrace(r) === traceCode,
  );
  if (boundElsewhere) {
    pushLog(traceCode, 'duplicate', '该追溯码已绑定在其他药品行');
    ElMessage.warning('追溯码重复');
    return 'duplicate';
  }

  const cur = effectiveTrace(row);
  if (!isTraceSlotEmpty(cur) && cur === traceCode) {
    pushLog(traceCode, 'ok', `已绑定（无需重复）：${row.itemName}`);
    return 'bound';
  }

  if (!isTraceSlotEmpty(cur) && cur !== traceCode) {
    pushLog(traceCode, 'ok', `解析更新：${row.itemName}（药品 id ${drugId}，覆盖原码）`);
  } else {
    pushLog(traceCode, 'ok', `已绑定：${row.itemName}（药品 id ${drugId}）`);
  }

  traceByLineId.value = { ...traceByLineId.value, [lineId]: traceCode };
  return 'bound';
}

/** 追加一条扫码流水（仅保留最近 80 条） */
function pushLog(code: string, status: TraceScanLogStatus, message: string): void {
  const timeLabel = new Date().toLocaleTimeString('zh-CN', { hour12: false });
  const id = `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;
  scanLog.value = [{ id, code, status, message, timeLabel }, ...scanLog.value].slice(0, 80);
}

/** 聚焦扫码输入框 */
function focusScanInput(): void {
  void nextTick(() => {
    scanInputRef.value?.focus?.();
  });
}

/**
 * 提交当前输入：支持多行/多段 20 位码；全部绑定后本轮回车直接关窗；
 * 输入为空且已全部绑定时回车亦关窗。
 */
function handleScanEnter(): void {
  const raw = scanInput.value;
  scanInput.value = '';

  if (!raw.trim()) {
    if (isAllBound()) {
      handleConfirm();
    } else {
      focusScanInput();
    }
    return;
  }

  const { bad, ok } = extractTraceCodes(raw);
  if (bad.length > 0) {
    for (const b of bad) {
      pushLog(b, 'invalid', `须为 ${TRACE_CODE_LENGTH} 位`);
    }
    ElMessage.warning(`有 ${bad.length} 段长度不符合 ${TRACE_CODE_LENGTH} 位，已跳过`);
  }

  for (const code of ok) {
    tryBindSingleCode(code);
  }

  void nextTick(() => {
    if (isAllBound()) {
      ElMessage.success('追溯码已全部绑定');
      handleConfirm();
      return;
    }
    focusScanInput();
  });
}

/** 取消：还原打开时的追溯码并关闭 */
function handleCancel(): void {
  traceByLineId.value = { ...snapshotTrace.value };
  visible.value = false;
}

/** 确定：保留当前绑定并关闭 */
function handleConfirm(): void {
  visible.value = false;
}

/** 移除单行已绑定的追溯码（便于扫错重扫） */
function clearLineTrace(row: DispensePrescriptionItemRow): void {
  traceByLineId.value = Object.fromEntries(
    Object.entries(traceByLineId.value).filter(([k]) => k !== row.id),
  );
  pushLog('—', 'ok', `已清除：${row.itemName}`);
}
</script>

<template>
  <AppDialog
    v-model="visible"
    title="批量扫描追溯码"
    width="920px"
    class="dispense-trace-scan-dialog"
    destroy-on-close
    @opened="handleDialogOpened"
  >
    <div class="dispense-trace-scan flex flex-col gap-3">
      <div class="text-sm text-[var(--el-text-color-secondary)]">
        <span v-if="props.patientName">患者：{{ props.patientName }} · </span>
        追溯码为
        <strong>{{ TRACE_CODE_LENGTH }}</strong> 位；可一次粘贴多行（每条后常带回车）或连续扫码。
        当前为<strong>前端模拟解析</strong>（与接口同形：码 → 药品 id + 明细行 +
        追溯码），对接后替换为真实请求。 全部明细已绑定后按回车将关闭本窗口。
        <span class="ml-1 font-medium text-[var(--el-text-color-primary)]">
          已绑定 {{ boundLineCount() }} / {{ props.lines.length }}
        </span>
      </div>

      <ElInput
        ref="scanInputRef"
        v-model="scanInput"
        clearable
        class="dispense-trace-scan-input font-mono"
        placeholder="扫描或粘贴（支持多条），按回车提交"
        @keydown.enter.prevent="handleScanEnter"
      />

      <div class="flex min-h-[280px] min-w-0 flex-1 gap-3">
        <div
          class="dispense-trace-scan-log flex min-w-0 flex-1 flex-col rounded border border-[var(--el-border-color-lighter)] bg-[var(--el-fill-color-blank)] p-2"
        >
          <div class="mb-1 text-xs font-medium text-[var(--el-text-color-secondary)]">扫码流水</div>
          <div class="min-h-0 flex-1 overflow-auto text-xs">
            <div
              v-if="scanLog.length === 0"
              class="py-6 text-center text-[var(--el-text-color-placeholder)]"
            >
              暂无记录，请开始扫码
            </div>
            <ul v-else class="m-0 list-none space-y-1.5 p-0">
              <li v-for="item in scanLog" :key="item.id" class="flex flex-wrap gap-x-2 break-all">
                <span class="shrink-0 text-[var(--el-text-color-placeholder)]">{{
                  item.timeLabel
                }}</span>
                <ElTag v-if="item.status === 'ok'" type="success" size="small" class="!shrink-0">
                  成功
                </ElTag>
                <ElTag
                  v-else-if="item.status === 'duplicate'"
                  type="warning"
                  size="small"
                  class="!shrink-0"
                >
                  重复
                </ElTag>
                <ElTag
                  v-else-if="item.status === 'invalid'"
                  type="danger"
                  size="small"
                  class="!shrink-0"
                >
                  格式
                </ElTag>
                <ElTag
                  v-else-if="item.status === 'not_found'"
                  type="info"
                  size="small"
                  class="!shrink-0"
                >
                  未找到
                </ElTag>
                <ElTag v-else type="info" size="small" class="!shrink-0">提示</ElTag>
                <span class="font-mono text-[var(--el-text-color-primary)]">{{ item.code }}</span>
                <span class="text-[var(--el-text-color-regular)]">{{ item.message }}</span>
              </li>
            </ul>
          </div>
        </div>

        <div class="dispense-trace-scan-table flex min-w-0 flex-[1.2] flex-col">
          <div class="mb-1 text-xs font-medium text-[var(--el-text-color-secondary)]">
            药品与追溯码
          </div>
          <ElTable
            :data="props.lines"
            border
            size="small"
            max-height="320"
            row-key="id"
            class="w-full min-w-0"
            empty-text="暂无处方明细"
          >
            <ElTableColumn prop="rxNo" label="处方号" width="128" show-overflow-tooltip />
            <ElTableColumn prop="itemName" label="项目名称" min-width="120" show-overflow-tooltip />
            <ElTableColumn prop="spec" label="规格" width="100" show-overflow-tooltip />
            <ElTableColumn label="追溯码" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                <span
                  :class="
                    isTraceSlotEmpty(effectiveTrace(row))
                      ? 'text-[var(--el-text-color-placeholder)]'
                      : 'font-mono text-[var(--el-text-color-primary)]'
                  "
                >
                  {{ isTraceSlotEmpty(effectiveTrace(row)) ? '待扫' : effectiveTrace(row) }}
                </span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="72" align="center" fixed="right">
              <template #default="{ row }">
                <ElButton
                  link
                  type="primary"
                  :disabled="isTraceSlotEmpty(effectiveTrace(row))"
                  @click="clearLineTrace(row)"
                >
                  清除
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="flex w-full justify-end gap-2">
        <ElButton @click="handleCancel">取消</ElButton>
        <ElButton type="primary" @click="handleConfirm">确定</ElButton>
      </div>
    </template>
  </AppDialog>
</template>

<style scoped>
.dispense-trace-scan-input :deep(.el-input__inner) {
  font-size: 13px;
}
</style>
