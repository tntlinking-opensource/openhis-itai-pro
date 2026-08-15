<script setup lang="ts">
import type { PrescriptionSheetData, PrescriptionSheetLine } from './prescription-sheet.types';

import { computed, ref } from 'vue';

import { ElButton, ElCheckbox, ElEmpty, ElTable, ElTableColumn } from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';
import { splitTraceNoCommaString } from '#/utils/trace';

defineOptions({
  name: 'PrescriptionSheetView',
});

const props = withDefaults(
  defineProps<{
    /**
     * 笺面左上角单号标签。
     * 门诊默认「处方号」；住院按日虚拟发药单传「发药单」。
     */
    prescriptionNoLabel?: string;
    /** 为 true 时略增大内边距与药品区高度，便于工作站阅读 */
    comfortable?: boolean;
    data: PrescriptionSheetData;
    /**
     * 笺面变体：`dispense` 发药工作站（追溯码/药房/批号/厂家）；
     * `doctorRx` 门诊医生站处方单（药名规格+数量 / 用法用量两行）
     */
    variant?: 'dispense' | 'doctorRx';
    /** 发药工作站：笺内勾选 + 发药 + 打印 */
    workbenchActions?: boolean;
    /** 是否选中（多选批量发药） */
    selected?: boolean;
    /** 是否允许勾选 */
    selectable?: boolean;
    /** 是否可单独发药 */
    canDispense?: boolean;
    /** 发药按钮禁用（提交中等） */
    dispenseDisabled?: boolean;
    /** 打印按钮禁用（打印中等） */
    printDisabled?: boolean;
    /** 打印按钮 loading（单张打印中） */
    printLoading?: boolean;
  }>(),
  {
    prescriptionNoLabel: '处方号',
    comfortable: false,
    variant: 'dispense',
    workbenchActions: false,
    selected: false,
    selectable: false,
    canDispense: false,
    dispenseDisabled: false,
    printDisabled: false,
    printLoading: false,
  },
);

const emit = defineEmits<{
  dispense: [];
  print: [];
  'update:selected': [value: boolean];
}>();

/** 追溯码弹窗 */
const traceDialogVisible = ref(false);
const traceDialogTitle = ref('追溯码');
const traceDialogCodes = ref<string[]>([]);

/** 库存剩余弹窗 */
const stockDialogVisible = ref(false);

/** 是否医生站处方单版式（无药房/追溯码/批号/厂家） */
const isDoctorRx = computed(() => props.variant === 'doctorRx');

/** 当前处方可查看的库存行 */
const stockDialogRows = computed(() => props.data.stockLines ?? []);

/** 印章色：按状态区分 */
const statusSealTone = computed(() => {
  const s = props.data.status;
  if (s === 'blocking') return 'danger';
  if (s === 'partial' || s === 'prepared') return 'warning';
  if (s === 'completed') return 'success';
  return 'pending';
});

/** 切换笺面勾选（仅可勾选时） */
function handleSelectChange(value: boolean | string | number) {
  if (!props.selectable) return;
  emit('update:selected', Boolean(value));
}

/** 点击笺面空白处切换勾选（避开按钮） */
function handleSheetClick() {
  if (!props.selectable) return;
  emit('update:selected', !props.selected);
}

/** 行是否有有效追溯码（非空、非占位） */
function hasTraceCodes(traceCode: string | undefined): boolean {
  return splitTraceNoCommaString(String(traceCode ?? '')).length > 0;
}

/** 打开药品行追溯码弹窗 */
function openTraceDialog(line: PrescriptionSheetLine) {
  const name = (line.itemName ?? '').trim() || '药品';
  traceDialogTitle.value = `追溯码 · ${name}`;
  traceDialogCodes.value = splitTraceNoCommaString(String(line.traceCode ?? ''));
  traceDialogVisible.value = true;
}

/** 打开当前处方药品剩余库存弹窗 */
function openStockDialog() {
  stockDialogVisible.value = true;
}

/** 医生站：规格展示（空/占位不输出） */
function formatDoctorRxSpec(spec: string | undefined): string {
  const text = String(spec ?? '').trim();
  if (!text || text === '—') return '';
  return text;
}

/** 医生站：用法用量展示（顿号改空格，对齐样张） */
function formatDoctorRxUsage(usage: string | undefined): string {
  const text = String(usage ?? '')
    .trim()
    .replaceAll(/[，,]/g, ' ')
    .replaceAll(/\s+/g, ' ');
  if (!text || text === '—') return '';
  return text;
}
</script>

<template>
  <!-- 笺面：纸质处方观感；工作站含勾选、发药、状态章 -->
  <div
    class="prescription-dialog-wrapper"
    :class="{ 'prescription-dialog-wrapper--comfortable': props.comfortable }"
  >
    <div
      class="prescription-container"
      :class="{
        'prescription-container--selectable': props.workbenchActions && props.selectable,
        'prescription-container--selected': props.workbenchActions && props.selected,
        'prescription-container--with-status': Boolean(props.data.statusLabel),
        'prescription-container--workbench': props.workbenchActions,
      }"
      @click="handleSheetClick"
    >
      <div
        v-if="props.data.statusLabel"
        class="prescription-sheet-status-seal"
        :class="`prescription-sheet-status-seal--${statusSealTone}`"
        aria-hidden="true"
      >
        <span class="prescription-sheet-status-seal__text">{{ props.data.statusLabel }}</span>
      </div>

      <div class="prescription-sheet-meta">
        <div class="prescription-sheet-no">
          <ElCheckbox
            v-if="props.workbenchActions"
            :model-value="props.selected"
            :disabled="!props.selectable"
            class="prescription-sheet-check"
            @click.stop
            @change="handleSelectChange"
          />
          <span class="prescription-sheet-no__label">{{ props.prescriptionNoLabel }}</span>
          <span class="prescription-sheet-no__value">{{ props.data.prescriptionNo }}</span>
        </div>
        <div v-if="props.workbenchActions" class="prescription-sheet-actions">
          <ElButton
            type="primary"
            plain
            size="small"
            class="prescription-sheet-action-btn"
            :disabled="props.printDisabled"
            @click.stop="emit('print')"
          >
            打印
          </ElButton>
          <ElButton
            type="primary"
            size="small"
            class="prescription-sheet-action-btn"
            :disabled="!props.canDispense || props.dispenseDisabled"
            @click.stop="emit('dispense')"
          >
            发药
          </ElButton>
        </div>
        <div v-else-if="isDoctorRx" class="prescription-sheet-actions">
          <ElButton
            type="primary"
            plain
            size="small"
            class="prescription-sheet-action-btn"
            :loading="props.printLoading"
            :disabled="props.printDisabled"
            @click.stop="emit('print')"
          >
            打印
          </ElButton>
        </div>
      </div>

      <header class="prescription-sheet-header">
        <div
          v-if="props.data.prescriptionTypeLine1 || props.data.prescriptionTypeLine2"
          class="prescription-sheet-type"
        >
          <div v-if="props.data.prescriptionTypeLine1" class="prescription-sheet-type__line">
            {{ props.data.prescriptionTypeLine1 }}
          </div>
          <div v-if="props.data.prescriptionTypeLine2" class="prescription-sheet-type__line">
            {{ props.data.prescriptionTypeLine2 }}
          </div>
        </div>
        <h2 class="prescription-sheet-hospital">{{ props.data.hospitalName }}</h2>
        <h3 class="prescription-sheet-doc-title">
          {{ props.data.documentTitle ?? '发药单' }}
        </h3>
        <div class="prescription-sheet-header-rule" aria-hidden="true"></div>
      </header>

      <section class="prescription-sheet-patient">
        <div class="prescription-sheet-row">
          <div class="prescription-sheet-field">
            <span class="item-label">姓名</span>
            <span class="item-value item-value--emphasize">{{ props.data.patientName }}</span>
          </div>
          <div class="prescription-sheet-field">
            <span class="item-label">年龄</span>
            <span class="item-value">{{ props.data.patientAgeLabel }}</span>
          </div>
          <div class="prescription-sheet-field">
            <span class="item-label">性别</span>
            <span class="item-value">{{ props.data.patientGenderLabel }}</span>
          </div>
        </div>
        <div class="prescription-sheet-row">
          <div class="prescription-sheet-field">
            <span class="item-label">科室</span>
            <span class="item-value">{{ props.data.deptName }}</span>
          </div>
          <div class="prescription-sheet-field">
            <!-- 费用性质固定本槽（职工/居民等）；就诊类型见方头「普通/慢病处方」 -->
            <span class="item-label">费用性质</span>
            <span class="item-value">{{ props.data.feeNatureLabel }}</span>
          </div>
          <div class="prescription-sheet-field">
            <span class="item-label">日期</span>
            <span class="item-value">{{ props.data.prescriptionDate }}</span>
          </div>
        </div>
        <div class="prescription-sheet-row">
          <div class="prescription-sheet-field prescription-sheet-field--grow">
            <span class="item-label">门诊号</span>
            <span class="item-value item-value--outpatient">{{
              props.data.outpatientNoLabel
            }}</span>
          </div>
          <div class="prescription-sheet-field">
            <span class="item-label">开单医生</span>
            <span class="item-value">{{ props.data.prescribingDoctorName }}</span>
          </div>
        </div>
        <div class="prescription-sheet-row prescription-sheet-row--diagnosis">
          <div class="prescription-sheet-field prescription-sheet-field--grow">
            <span class="item-label">诊断</span>
            <span class="item-value item-value--diagnosis">{{ props.data.diagnosis }}</span>
          </div>
          <div class="prescription-sheet-field">
            <span class="item-label">手机号</span>
            <span class="item-value">{{ props.data.patientPhone }}</span>
          </div>
        </div>
      </section>

      <section class="prescription-sheet-rx">
        <div class="rp-heading">
          <span class="rp-mark">Rp</span>
          <span v-if="!isDoctorRx" class="rp-pharmacy">
            <span class="rp-pharmacy__label">发放药房</span>
            <span class="rp-pharmacy__value">{{ props.data.dispensePharmacyLabel }}</span>
            <ElButton
              type="primary"
              link
              size="small"
              class="rp-pharmacy__stock-btn"
              @click.stop="openStockDialog"
            >
              查看库存
            </ElButton>
          </span>
        </div>
        <!-- 西药：明细两行；中药：四列药名+克数，右上角特殊煎法 -->
        <div class="medicen-list">
          <template v-if="props.data.rxType === 'herbal'">
            <div v-if="(props.data.tcmHerbGridRows?.length ?? 0) > 0" class="tcm-herb-grid">
              <div
                v-for="(gridRow, gridIdx) in props.data.tcmHerbGridRows"
                :key="gridIdx"
                class="tcm-herb-grid-row"
              >
                <div v-if="gridRow.col1" class="tcm-herb-cell">
                  <span class="tcm-herb-cell__name">{{ gridRow.col1 }}</span>
                  <span v-if="gridRow.note1" class="tcm-herb-cell__note">{{ gridRow.note1 }}</span>
                </div>
                <div v-if="gridRow.col2" class="tcm-herb-cell">
                  <span class="tcm-herb-cell__name">{{ gridRow.col2 }}</span>
                  <span v-if="gridRow.note2" class="tcm-herb-cell__note">{{ gridRow.note2 }}</span>
                </div>
                <div v-if="gridRow.col3" class="tcm-herb-cell">
                  <span class="tcm-herb-cell__name">{{ gridRow.col3 }}</span>
                  <span v-if="gridRow.note3" class="tcm-herb-cell__note">{{ gridRow.note3 }}</span>
                </div>
                <div v-if="gridRow.col4" class="tcm-herb-cell">
                  <span class="tcm-herb-cell__name">{{ gridRow.col4 }}</span>
                  <span v-if="gridRow.note4" class="tcm-herb-cell__note">{{ gridRow.note4 }}</span>
                </div>
              </div>
            </div>
            <div v-if="props.data.tcmUsageText" class="tcm-usage-text">
              {{ props.data.tcmUsageText }}
            </div>
            <div v-if="props.data.decoctionChargeAmountLabel" class="tcm-decoction-fee">
              <span class="tcm-decoction-fee__name">
                {{ props.data.decoctionActivityName || '代煎中药' }}
              </span>
              <span v-if="props.data.decoctionChargeQuantityLabel" class="tcm-decoction-fee__qty">
                X{{ props.data.decoctionChargeQuantityLabel }}
              </span>
              <span class="tcm-decoction-fee__amount">
                代煎费：{{ props.data.decoctionChargeAmountLabel }}
              </span>
            </div>
          </template>
          <template v-else-if="isDoctorRx">
            <!-- 医生站：药名+规格 | 数量；下一行缩进用法用量 -->
            <div
              v-for="(line, idx) in props.data.lines ?? []"
              :key="idx"
              class="medicine-line medicine-line--doctor-rx"
            >
              <div class="medicine-line__main medicine-line__main--doctor-rx">
                <span class="medicine-line__name">
                  {{ line.itemName }}
                  <template v-if="formatDoctorRxSpec(line.spec)">
                    {{ ' ' + formatDoctorRxSpec(line.spec) }}
                  </template>
                </span>
                <span v-if="line.quantity && line.quantity !== '—'" class="medicine-line__qty">
                  {{ line.quantity }}
                </span>
              </div>
              <div v-if="formatDoctorRxUsage(line.usage)" class="medicine-line__usage-row">
                用法用量：{{ formatDoctorRxUsage(line.usage) }}
              </div>
            </div>
          </template>
          <template v-else>
            <div v-for="(line, idx) in props.data.lines ?? []" :key="idx" class="medicine-line">
              <div class="medicine-line__main">
                <span class="medicine-line__idx">{{ idx + 1 }}.</span>
                <span class="medicine-line__title">
                  {{ line.itemName }}
                  <template v-if="line.spec && line.spec !== '—'">({{ line.spec }})</template>
                  <template v-if="line.usage"> {{ line.usage }}</template>
                  <template v-if="line.quantity && line.quantity !== '—'">
                    X{{ line.quantity }}
                  </template>
                </span>
                <ElButton
                  link
                  type="primary"
                  size="small"
                  class="medicine-line__trace-btn"
                  :disabled="!hasTraceCodes(line.traceCode)"
                  @click.stop="openTraceDialog(line)"
                >
                  追溯码
                </ElButton>
              </div>
              <div class="medicine-line__sub">
                <span>单价：{{ line.amount }}</span>
                <span>批号：{{ line.batchNo }}</span>
                <span>厂家：{{ line.factory }}</span>
              </div>
            </div>
          </template>
        </div>
      </section>

      <footer class="prescription-sheet-footer">
        <div class="prescription-sheet-row">
          <div class="prescription-sheet-field">
            <span class="item-label">医师</span>
            <span class="item-value">{{ props.data.footerDoctorName }}</span>
          </div>
          <div class="prescription-sheet-field">
            <span class="item-label">划价</span>
            <span class="item-value">{{ props.data.footerPricingClerkName }}</span>
          </div>
          <div class="prescription-sheet-field">
            <span class="item-label">合计</span>
            <span class="item-value item-value--amount">{{ props.data.totalAmountLabel }}</span>
          </div>
        </div>
        <div class="prescription-sheet-row">
          <div class="prescription-sheet-field">
            <span class="item-label">调配</span>
            <span class="item-value">{{ props.data.footerDispensingName }}</span>
          </div>
          <div class="prescription-sheet-field">
            <span class="item-label">发药</span>
            <span class="item-value">{{ props.data.footerDeliverName }}</span>
          </div>
        </div>
      </footer>
    </div>
  </div>

  <AppDialog v-model="traceDialogVisible" :title="traceDialogTitle" width="480px" destroy-on-close>
    <ul v-if="traceDialogCodes.length > 0" class="sheet-trace-dialog-list">
      <li v-for="code in traceDialogCodes" :key="code" class="sheet-trace-dialog-list__item">
        {{ code }}
      </li>
    </ul>
    <div v-else class="sheet-trace-dialog-empty">暂无追溯码</div>
  </AppDialog>

  <AppDialog v-model="stockDialogVisible" title="药品剩余库存" width="560px" destroy-on-close>
    <ElTable
      v-if="stockDialogRows.length > 0"
      :data="stockDialogRows"
      size="small"
      max-height="360"
      class="sheet-stock-dialog-table w-full"
    >
      <ElTableColumn
        prop="itemName"
        label="药品名称"
        min-width="180"
        align="center"
        header-align="center"
        show-overflow-tooltip
      />
      <ElTableColumn
        prop="batchNo"
        label="批号"
        width="120"
        align="center"
        header-align="center"
        show-overflow-tooltip
      />
      <ElTableColumn
        prop="remainingStockText"
        label="剩余库存"
        width="140"
        align="center"
        header-align="center"
        show-overflow-tooltip
      />
    </ElTable>
    <ElEmpty v-else description="暂无库存数据" :image-size="64" />
  </AppDialog>
</template>

<style lang="scss" scoped>
.prescription-dialog-wrapper {
  display: flex;
  flex-wrap: nowrap;
  gap: 0;
  justify-content: flex-start;
  width: auto;
  max-width: 100%;
  min-height: 0;
  background-color: transparent;
}

.prescription-dialog-wrapper--comfortable {
  .prescription-container {
    padding: 16px 18px 18px;
  }
}

.prescription-container {
  /* 贴近系统主题：主色/文字/描边走 Element Plus 变量 */
  --rx-ink: var(--el-text-color-primary);
  --rx-muted: var(--el-text-color-regular);
  --rx-line: var(--el-border-color-lighter);
  --rx-accent: var(--el-color-primary);
  --rx-accent-soft: var(--el-color-primary-light-9);
  --rx-rp: var(--el-color-danger);
  --rx-paper: var(--el-bg-color);

  position: relative;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  width: 500px;
  max-width: 100%;
  /* 固定笺面高度，多张发药单视觉对齐；药品区溢出内部滚动 */
  height: 660px;
  padding: 14px 16px 16px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  background: linear-gradient(180deg, var(--rx-accent-soft) 0%, transparent 72px), var(--rx-paper);
  box-shadow:
    0 1px 0 rgb(255 255 255 / 80%) inset,
    0 10px 28px rgb(15 23 42 / 7%);
  font-size: 13px;
  color: var(--rx-ink);
}

.prescription-container--workbench {
  display: flex;
  flex-direction: column;
}

.prescription-container--selectable {
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}

.prescription-container--selected {
  border-color: var(--el-color-primary-light-5);
  box-shadow:
    0 0 0 1px var(--el-color-primary-light-8),
    0 12px 28px rgb(15 23 42 / 1%);
}

.prescription-container--with-status {
  .prescription-sheet-meta {
    padding-right: 76px;
  }

  .prescription-sheet-actions {
    margin-right: 4px;
  }
}

/** 长方形状态章：右上角斜盖 */
.prescription-sheet-status-seal {
  position: absolute;
  top: 14px;
  right: 14px;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 48px;
  padding: 5px 11px;
  border: 2px solid currentColor;
  border-radius: 3px;
  background: color-mix(in srgb, var(--rx-paper) 72%, transparent);
  opacity: 0.92;
  pointer-events: none;
  transform: rotate(-11deg);
  box-sizing: border-box;

  &::before {
    content: '';
    position: absolute;
    inset: 2px;
    border: 1px solid currentColor;
    border-radius: 1px;
    pointer-events: none;
  }
}

.prescription-sheet-status-seal__text {
  position: relative;
  z-index: 1;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: 0.18em;
  text-indent: 0.18em;
  white-space: nowrap;
}

.prescription-sheet-status-seal--pending {
  /* 待发章保留经典印泥红，其余走语义色 */
  color: var(--el-color-danger);
}

.prescription-sheet-status-seal--warning {
  color: var(--el-color-warning);
}

.prescription-sheet-status-seal--danger {
  color: var(--el-color-danger);
}

.prescription-sheet-status-seal--success {
  color: var(--el-color-success);
}

.prescription-sheet-status-seal--muted {
  color: var(--el-color-info);
}

.prescription-sheet-meta {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 32px;
  margin: -2px 0 10px;
}

.prescription-sheet-header {
  position: relative;
  flex-shrink: 0;
  margin-bottom: 14px;
  text-align: center;
}

.prescription-sheet-type {
  position: absolute;
  top: 0;
  right: 0;
  max-width: 72px;
  font-size: 12px;
  line-height: 1.35;
  color: var(--rx-ink);
  text-align: center;
}

.prescription-sheet-type__line {
  white-space: nowrap;
}

.prescription-sheet-no {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  font-size: 12px;
}

.prescription-sheet-no__label {
  flex-shrink: 0;
  color: var(--rx-muted);
}

.prescription-sheet-no__value {
  min-width: 0;
  overflow: hidden;
  font-weight: 600;
  color: var(--rx-ink);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.prescription-sheet-check {
  margin-right: 2px;
}

.prescription-sheet-actions {
  display: flex;
  flex-shrink: 0;
  gap: 6px;
  align-items: center;
}

.prescription-sheet-action-btn {
  min-width: 52px;
  border-radius: 6px;
}

.prescription-sheet-hospital {
  margin: 0;
  font-family: 'Source Han Serif SC', 'Noto Serif SC', 'Songti SC', 'STSong', serif;
  font-size: 1.35rem;
  font-weight: 700;
  line-height: 1.35;
  letter-spacing: 0.08em;
  color: var(--el-color-primary-dark-2);
}

.prescription-sheet-doc-title {
  margin: 4px 0 0;
  font-size: 1.05rem;
  font-weight: 600;
  letter-spacing: 0.28em;
  text-indent: 0.28em;
  color: var(--rx-accent);
}

.prescription-sheet-header-rule {
  width: 72px;
  height: 3px;
  margin: 10px auto 0;
  border-radius: 999px;
  background: linear-gradient(90deg, transparent, var(--rx-accent), transparent);
}

.prescription-sheet-patient {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  gap: 8px;
  padding: 12px 12px 10px;
  margin-bottom: 12px;
  border: 1px solid var(--rx-line);
  border-radius: 8px;
  background: rgb(255 255 255 / 70%);
}

.prescription-sheet-rx {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
  margin-bottom: 12px;
}

.prescription-sheet-footer {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px dashed var(--el-border-color);
}

.prescription-sheet-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.prescription-sheet-row--diagnosis {
  padding-top: 8px;
  border-top: 1px dashed var(--rx-line);
}

.prescription-sheet-field {
  display: flex;
  flex: 1 1 0;
  flex-flow: row nowrap;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
}

.prescription-sheet-field--grow {
  flex: 1.35 1 0;
}

.prescription-sheet-field--block {
  flex: 1 1 100%;
}

.item-label {
  flex: 0 0 auto;
  font-size: 12px;
  color: var(--rx-muted);
  white-space: nowrap;

  &::after {
    content: '';
  }
}

.item-value {
  flex: 1 1 auto;
  min-width: 0;
  font-weight: 500;
  color: var(--rx-ink);
}

.item-value--emphasize {
  font-weight: 700;
}

.item-value--amount {
  font-weight: 700;
  color: var(--rx-accent);
}

.item-value--outpatient,
.item-value--diagnosis {
  overflow-wrap: anywhere;
  word-break: break-all;
}

.rp-heading {
  display: flex;
  width: 100%;
  box-sizing: border-box;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.rp-mark {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  min-width: 36px;
  padding: 2px 8px 2px 6px;
  border-radius: 4px;
  background: var(--el-color-danger-light-9);
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 15px;
  font-weight: 700;
  font-style: italic;
  letter-spacing: 0.04em;
  color: var(--rx-rp);
}

.rp-pharmacy {
  display: inline-flex;
  flex: 0 1 auto;
  gap: 4px;
  align-items: baseline;
  justify-content: flex-end;
  min-width: 0;
  max-width: calc(100% - 48px);
  margin-left: auto;
  font-size: 12px;
  line-height: 1.4;
  text-align: right;
}

.rp-pharmacy__label {
  flex-shrink: 0;
  color: var(--rx-muted);

  &::after {
    content: '：';
  }
}

.rp-pharmacy__value {
  min-width: 0;
  overflow: hidden;
  font-weight: 500;
  color: var(--rx-ink);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rp-pharmacy__stock-btn {
  flex-shrink: 0;
  margin-left: 2px;
  height: auto;
  padding: 0;
  font-size: 12px;
  line-height: 1.4;
  vertical-align: baseline;
}

.medicen-list {
  box-sizing: border-box;
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
  padding: 10px 12px;
  overflow-x: hidden;
  overflow-y: auto;
  border: 1px solid var(--rx-line);
  border-radius: 8px;
  background: var(--rx-accent-soft);
}

.tcm-herb-grid {
  flex: 1 1 auto;
  min-height: 0;
}

.tcm-herb-grid-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px 12px;
  margin-bottom: 10px;
}

.tcm-herb-cell {
  min-height: 1.4em;
  line-height: 1.45;
  color: var(--rx-ink);
  overflow-wrap: anywhere;
  word-break: break-all;
}

/* 特殊煎法：贴药名右上角，对齐打印模版 note 上标位 */
.tcm-herb-cell__note {
  margin-left: 2px;
  font-size: 11px;
  line-height: 1;
  color: var(--rx-muted);
  white-space: nowrap;
  position: relative;
  top: -8px;
}

.tcm-usage-text {
  flex-shrink: 0;
  margin-top: 10px;
  font-weight: 500;
  color: var(--rx-muted);
  text-align: right;
}

.tcm-decoction-fee {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  align-items: baseline;
  justify-content: flex-end;
  margin-top: 8px;
  font-weight: 500;
  color: var(--rx-ink);
}

.tcm-decoction-fee__name {
  color: var(--rx-ink);
}

.tcm-decoction-fee__qty,
.tcm-decoction-fee__amount {
  color: var(--rx-muted);
}

.medicine-line {
  margin-bottom: 12px;

  &:last-child {
    margin-bottom: 0;
  }
}

.medicine-line__main {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
  align-items: baseline;
  line-height: 1.45;
}

.medicine-line__idx {
  flex-shrink: 0;
  font-weight: 600;
  color: var(--rx-muted);
}

.medicine-line__title {
  flex: 1 1 auto;
  min-width: 0;
  font-weight: 600;
  color: var(--rx-ink);
}

.medicine-line__usage {
  font-size: 12px;
  font-weight: 500;
  color: var(--rx-muted);
}

.medicine-line__trace-btn {
  flex-shrink: 0;
  height: auto;
  padding: 0;
  margin-left: auto;
  font-size: 12px;
}

.medicine-line__sub {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  align-items: center;
  margin-top: 4px;
  margin-left: 1.2em;
  font-size: 12px;
  line-height: 1.45;
  color: var(--rx-muted);
  overflow-wrap: anywhere;
  word-break: break-all;
}

.medicine-line__main--doctor-rx {
  flex-wrap: nowrap;
  justify-content: space-between;
  gap: 12px;
}

.medicine-line__name {
  flex: 1 1 auto;
  min-width: 0;
  font-weight: 600;
  color: var(--rx-ink);
  overflow-wrap: anywhere;
  word-break: break-all;
}

.medicine-line__qty {
  flex-shrink: 0;
  font-weight: 600;
  color: var(--rx-ink);
  white-space: nowrap;
}

.medicine-line__usage-row {
  margin-top: 4px;
  padding-left: 2em;
  font-size: 13px;
  line-height: 1.45;
  color: var(--rx-muted);
  overflow-wrap: anywhere;
  word-break: break-all;
}

.sheet-trace-dialog-list {
  padding: 0;
  margin: 0;
  list-style: none;
}

.sheet-trace-dialog-list__item {
  padding: 8px 0;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-primary);
  letter-spacing: 0.03em;
  word-break: break-all;
}

.sheet-trace-dialog-list__item + .sheet-trace-dialog-list__item {
  border-top: 1px solid var(--el-border-color-extra-light);
}

.sheet-trace-dialog-empty {
  padding: 16px 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  text-align: center;
}
</style>
