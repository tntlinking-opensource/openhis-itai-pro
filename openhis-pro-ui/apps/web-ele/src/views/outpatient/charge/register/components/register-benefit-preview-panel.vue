<script setup lang="ts">
import type {
  YbInsuInfoPreviewDto,
  YbRegisterBenefitPreviewDto,
} from '#/api/outpatient-registration';

import { computed, ref, watch } from 'vue';

import {
  ElAlert,
  ElDescriptions,
  ElDescriptionsItem,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { formatInsutype } from '#/chs/constants/reconciliation';
import { formatMedType } from '#/chs/constants/medical-info-query';

import { formatInsuplcAdmdvsDisplay } from '../services/charge-type-category';
import { loadRegisterBenefitPreview } from '../services/register-benefit.service';
import {
  benefitAreaAlertType,
  benefitCredentialLabel,
  displayBenefitDash,
  formatBenefitBalc,
  formatBenefitGend,
  formatBenefitPsnInsuStas,
  formatBenefitPsnType,
  formatCumTypeCode,
  formatCvlservFlag,
  formatFundPayType,
  formatTreatmentFlag,
  isTreatmentEnjoyed,
} from './register-benefit-display.utils';

defineOptions({
  name: 'RegisterBenefitPreviewPanel',
});

const props = withDefaults(
  defineProps<{
    cardNo?: string;
    contractId?: string;
    /** 2001 医疗类别；门诊 11，住院登记 21 */
    medType?: string;
    mediumType?: string;
    preview: YbRegisterBenefitPreviewDto;
    /** false=Tab 内嵌：表格不单独限高，避免滚动条嵌套 */
    scrollableTables?: boolean;
    /** 默认展示全部待遇检查基金项（含不享受） */
    defaultShowAllTreatment?: boolean;
    /** 父级异步拉取待遇明细中（读卡后 2001/5301/5206） */
    externalTreatmentLoading?: boolean;
    /** vertical：挂号页左侧紧凑字段区 */
    layout?: 'default' | 'vertical';
    /** 父级已展示费用性质匹配提示时隐藏本面板区划 Alert，避免重复 */
    suppressAreaAlert?: boolean;
    ybCardInfo?: string;
    ybCardSn?: string;
    ybCertno?: string;
    ybMediumType?: string;
  }>(),
  {
    scrollableTables: true,
    medType: '11',
    defaultShowAllTreatment: false,
    externalTreatmentLoading: false,
    layout: 'default',
    suppressAreaAlert: false,
  },
);

const previewModel = ref<YbRegisterBenefitPreviewDto>({ ...props.preview });
const selectedInsuIndex = ref(0);
const treatmentLoading = ref(false);
/** 默认仅展示享受待遇的基金项；挂号页可配置为全部展示 */
const showNotEnjoyedTreatment = ref(props.defaultShowAllTreatment);

const panelTreatmentLoading = computed(
  () => treatmentLoading.value || props.externalTreatmentLoading,
);

const tableMaxHeight = computed(() => (props.scrollableTables ? 160 : undefined));
const opspTableMaxHeight = computed(() => (props.scrollableTables ? 140 : undefined));

const selectedInsuRow = computed<null | YbInsuInfoPreviewDto>(() => {
  const list = previewModel.value.insuinfoList ?? [];
  if (list.length === 0) {
    return null;
  }
  const idx = Math.min(Math.max(selectedInsuIndex.value, 0), list.length - 1);
  return list[idx] ?? null;
});

const areaAlertType = computed(() => benefitAreaAlertType(previewModel.value.areaHint?.areaKind));

/** 仅异地/未知时展示区划提示；本地成功文案不占版面；父级已提示时不重复 */
const showAreaAlert = computed(() => {
  if (props.suppressAreaAlert) {
    return false;
  }
  const hint = previewModel.value.areaHint;
  if (!hint?.hintMessage) {
    return false;
  }
  const kind = hint.areaKind;
  return kind === 'IN_PROVINCE' || kind === 'CROSS_PROVINCE' || kind === 'UNKNOWN';
});

const insuplcDisplay = computed(() =>
  formatInsuplcAdmdvsDisplay(
    selectedInsuRow.value?.insuplcAdmdvs ?? previewModel.value.insuplcAdmdvs,
  ),
);

const mdtrtDisplay = computed(() =>
  formatInsuplcAdmdvsDisplay(previewModel.value.areaHint?.mdtrtareaAdmdvs),
);

const balcDisplay = computed(() =>
  formatBenefitBalc(selectedInsuRow.value?.balc ?? previewModel.value.balc),
);

const treatmentCheckTitle = computed(() => `待遇检查（${formatMedType(props.medType ?? '11')}）`);

const allTreatmentRows = computed(() => previewModel.value.treatmentInfoList ?? []);

const notEnjoyedTreatmentCount = computed(
  () => allTreatmentRows.value.filter((row) => !isTreatmentEnjoyed(row.trtEnjymntFlag)).length,
);

const displayedTreatmentRows = computed(() => {
  if (showNotEnjoyedTreatment.value) {
    return allTreatmentRows.value;
  }
  return allTreatmentRows.value.filter((row) => isTreatmentEnjoyed(row.trtEnjymntFlag));
});

const treatmentTableEmptyText = computed(() => {
  if (panelTreatmentLoading.value && allTreatmentRows.value.length === 0) {
    return '正在加载待遇信息…';
  }
  if (allTreatmentRows.value.length === 0) {
    return '暂无待遇检查数据';
  }
  if (
    !showNotEnjoyedTreatment.value &&
    notEnjoyedTreatmentCount.value > 0 &&
    displayedTreatmentRows.value.length === 0
  ) {
    return '当前基金项均不享受待遇，可打开开关查看明细';
  }
  if (!showNotEnjoyedTreatment.value && displayedTreatmentRows.value.length === 0) {
    return '暂无享受待遇的基金项';
  }
  return '暂无待遇检查数据';
});

function syncPreview(next: YbRegisterBenefitPreviewDto) {
  previewModel.value = next;
  selectedInsuIndex.value = 0;
  showNotEnjoyedTreatment.value = props.defaultShowAllTreatment;
}

watch(
  () => props.preview,
  (next) => {
    syncPreview(next);
  },
  { deep: true },
);

async function refreshTreatmentForSelectedInsu() {
  const insu = selectedInsuRow.value;
  const contractId = props.contractId?.trim();
  const psnNo = previewModel.value.psnNo?.trim();
  if (!contractId || !psnNo || !insu?.insutype) {
    return;
  }
  treatmentLoading.value = true;
  try {
    const result = await loadRegisterBenefitPreview({
      contractId,
      insuplcAdmdvs: insu.insuplcAdmdvs ?? previewModel.value.insuplcAdmdvs,
      insutype: insu.insutype,
      medType: props.medType,
      psnNo,
      runCumulativeQuery: false,
      runOpspFilingQuery: false,
      runTreatmentCheck: true,
      ybCardInfo: props.ybCardInfo,
      ybCardSn: props.ybCardSn || undefined,
      ybCertno: props.ybCertno || undefined,
      ybMediumType: props.ybMediumType,
    });
    if (result.ybPreview) {
      previewModel.value = {
        ...previewModel.value,
        areaHint: result.ybPreview.areaHint ?? previewModel.value.areaHint,
        insuplcAdmdvs: insu.insuplcAdmdvs ?? previewModel.value.insuplcAdmdvs,
        insutype: insu.insutype,
        balc: insu.balc ?? previewModel.value.balc,
        treatmentBlocked: result.ybPreview.treatmentBlocked,
        treatmentInfoList: result.ybPreview.treatmentInfoList,
      };
    }
  } finally {
    treatmentLoading.value = false;
  }
}

async function handleInsuRowClick(row: YbInsuInfoPreviewDto) {
  const list = previewModel.value.insuinfoList ?? [];
  const index = list.indexOf(row);
  selectedInsuIndex.value = Math.max(index, 0);
  await refreshTreatmentForSelectedInsu();
}

function handleInsuChipClick(index: number) {
  const list = previewModel.value.insuinfoList ?? [];
  const row = list[index];
  if (!row) {
    return;
  }
  void handleInsuRowClick(row);
}

const credentialLabel = computed(() => benefitCredentialLabel(props.mediumType));

defineExpose({
  syncPreview,
});
</script>

<template>
  <div
    class="register-benefit-preview-panel text-sm"
    :class="{ 'register-benefit-preview-panel--vertical': layout === 'vertical' }"
  >
    <ElAlert
      v-if="showAreaAlert"
      :type="areaAlertType"
      :closable="false"
      show-icon
      :title="previewModel.areaHint?.hintMessage"
      class="register-benefit-preview-panel__alert"
    />

    <template v-if="layout === 'vertical'">
      <div
        v-if="(previewModel.insuinfoList?.length ?? 0) > 1"
        class="register-benefit-preview-panel__insu-strip"
      >
        <button
          v-for="(row, index) in previewModel.insuinfoList"
          :key="`${row.insutype}-${index}`"
          type="button"
          class="register-benefit-preview-panel__insu-chip"
          :class="{ 'is-active': selectedInsuIndex === index }"
          @click="handleInsuChipClick(index)"
        >
          {{ formatInsutype(row.insutype) }}
        </button>
      </div>

      <div class="register-benefit-preview-panel__hero">
        <div class="register-benefit-preview-panel__hero-main">
          <span class="register-benefit-preview-panel__hero-name">
            {{ displayBenefitDash(previewModel.psnName) }}
          </span>
          <span class="register-benefit-preview-panel__hero-meta">
            {{ formatBenefitGend(previewModel.gend) }}
            <template v-if="previewModel.brdy"> · {{ previewModel.brdy }}</template>
          </span>
          <span class="register-benefit-preview-panel__hero-insu">
            {{ formatInsutype(selectedInsuRow?.insutype ?? previewModel.insutype) }}
          </span>
        </div>
        <div class="register-benefit-preview-panel__hero-balc">
          <span class="register-benefit-preview-panel__hero-balc-label">个账余额</span>
          <span class="register-benefit-preview-panel__hero-balc-value">{{ balcDisplay }}</span>
        </div>
      </div>

      <div class="register-benefit-preview-panel__groups">
        <div class="register-benefit-preview-panel__group">
          <div class="register-benefit-preview-panel__group-title">身份信息</div>
          <div class="register-benefit-preview-panel__field-grid">
            <div class="register-benefit-preview-panel__field">
              <div class="register-benefit-preview-panel__field-label">证件号</div>
              <div
                class="register-benefit-preview-panel__field-value"
                :title="displayBenefitDash(previewModel.certno)"
              >
                {{ displayBenefitDash(previewModel.certno) }}
              </div>
            </div>
            <div class="register-benefit-preview-panel__field">
              <div class="register-benefit-preview-panel__field-label">人员编号</div>
              <div
                class="register-benefit-preview-panel__field-value"
                :title="displayBenefitDash(previewModel.psnNo)"
              >
                {{ displayBenefitDash(previewModel.psnNo) }}
              </div>
            </div>
            <div class="register-benefit-preview-panel__field">
              <div class="register-benefit-preview-panel__field-label">{{ credentialLabel }}</div>
              <div
                class="register-benefit-preview-panel__field-value"
                :title="displayBenefitDash(cardNo)"
              >
                {{ displayBenefitDash(cardNo) }}
              </div>
            </div>
          </div>
        </div>

        <div class="register-benefit-preview-panel__group">
          <div class="register-benefit-preview-panel__group-title">参保信息</div>
          <div class="register-benefit-preview-panel__field-grid">
            <div class="register-benefit-preview-panel__field">
              <div class="register-benefit-preview-panel__field-label">参保地</div>
              <div
                class="register-benefit-preview-panel__field-value register-benefit-preview-panel__field-value--emphasis"
              >
                {{ insuplcDisplay }}
              </div>
            </div>
            <div class="register-benefit-preview-panel__field">
              <div class="register-benefit-preview-panel__field-label">就医地</div>
              <div class="register-benefit-preview-panel__field-value">{{ mdtrtDisplay }}</div>
            </div>
            <div class="register-benefit-preview-panel__field">
              <div class="register-benefit-preview-panel__field-label">人员类别</div>
              <div class="register-benefit-preview-panel__field-value">
                {{ formatBenefitPsnType(selectedInsuRow?.psnType) }}
              </div>
            </div>
            <div class="register-benefit-preview-panel__field">
              <div class="register-benefit-preview-panel__field-label">参保状态</div>
              <div class="register-benefit-preview-panel__field-value">
                {{ formatBenefitPsnInsuStas(selectedInsuRow?.psnInsuStas) }}
              </div>
            </div>
            <div class="register-benefit-preview-panel__field">
              <div class="register-benefit-preview-panel__field-label">参保日期</div>
              <div class="register-benefit-preview-panel__field-value">
                {{ displayBenefitDash(selectedInsuRow?.psnInsuDate) }}
              </div>
            </div>
            <div class="register-benefit-preview-panel__field">
              <div class="register-benefit-preview-panel__field-label">公务员</div>
              <div class="register-benefit-preview-panel__field-value">
                {{ formatCvlservFlag(selectedInsuRow?.cvlservFlag) }}
              </div>
            </div>
            <div
              class="register-benefit-preview-panel__field register-benefit-preview-panel__field--span-3"
            >
              <div class="register-benefit-preview-panel__field-label">单位名称</div>
              <div class="register-benefit-preview-panel__field-value">
                {{ displayBenefitDash(selectedInsuRow?.empName) }}
              </div>
            </div>
          </div>
        </div>

        <div class="register-benefit-preview-panel__group">
          <div class="register-benefit-preview-panel__group-title">{{ treatmentCheckTitle }}</div>
          <div
            v-if="displayedTreatmentRows.length > 0"
            class="register-benefit-preview-panel__field-grid"
          >
            <template v-for="(row, index) in displayedTreatmentRows" :key="`trt-${index}`">
              <div
                class="register-benefit-preview-panel__field register-benefit-preview-panel__field--span-2"
              >
                <div class="register-benefit-preview-panel__field-label">基金类型</div>
                <div class="register-benefit-preview-panel__field-value">
                  {{ formatFundPayType(row.fundPayType) }}
                </div>
              </div>
              <div class="register-benefit-preview-panel__field">
                <div class="register-benefit-preview-panel__field-label">享受标志</div>
                <div class="register-benefit-preview-panel__field-value">
                  {{ formatTreatmentFlag(row.trtEnjymntFlag) }}
                </div>
              </div>
              <div class="register-benefit-preview-panel__field">
                <div class="register-benefit-preview-panel__field-label">有效期</div>
                <div class="register-benefit-preview-panel__field-value">
                  {{ displayBenefitDash(row.begndate) }}-{{ displayBenefitDash(row.enddate) }}
                </div>
              </div>
              <div
                v-if="row.trtChkRslt"
                class="register-benefit-preview-panel__field register-benefit-preview-panel__field--span-3"
              >
                <div class="register-benefit-preview-panel__field-label">不享受原因</div>
                <div class="register-benefit-preview-panel__field-value">
                  {{ row.trtChkRslt }}
                </div>
              </div>
            </template>
          </div>
          <div v-else class="register-benefit-preview-panel__group-empty">
            <ElTag v-if="panelTreatmentLoading" type="info" size="small">查询中</ElTag>
            <template v-else>{{ treatmentTableEmptyText }}</template>
          </div>
        </div>

        <div class="register-benefit-preview-panel__group">
          <div class="register-benefit-preview-panel__group-title">慢特病备案</div>
          <div
            v-if="(previewModel.opspFilingList?.length ?? 0) > 0"
            class="register-benefit-preview-panel__field-grid"
          >
            <template v-for="(row, index) in previewModel.opspFilingList" :key="`opsp-${index}`">
              <div
                class="register-benefit-preview-panel__field register-benefit-preview-panel__field--span-2"
              >
                <div class="register-benefit-preview-panel__field-label">病种</div>
                <div class="register-benefit-preview-panel__field-value">
                  {{ displayBenefitDash(row.opspDiseName) }}
                </div>
              </div>
              <div class="register-benefit-preview-panel__field">
                <div class="register-benefit-preview-panel__field-label">开始</div>
                <div class="register-benefit-preview-panel__field-value">
                  {{ displayBenefitDash(row.begndate) }}
                </div>
              </div>
              <div class="register-benefit-preview-panel__field">
                <div class="register-benefit-preview-panel__field-label">结束</div>
                <div class="register-benefit-preview-panel__field-value">
                  {{ displayBenefitDash(row.enddate) }}
                </div>
              </div>
            </template>
          </div>
          <div v-else class="register-benefit-preview-panel__group-empty">
            <ElTag v-if="panelTreatmentLoading" type="info" size="small">加载中</ElTag>
            <template v-else>—</template>
          </div>
        </div>

        <div
          v-if="(previewModel.cumulativeInfoList?.length ?? 0) > 0 || panelTreatmentLoading"
          class="register-benefit-preview-panel__group"
        >
          <div class="register-benefit-preview-panel__group-title">费用累计（当月）</div>
          <div
            v-if="(previewModel.cumulativeInfoList?.length ?? 0) > 0"
            class="register-benefit-preview-panel__field-grid"
          >
            <template v-for="(row, index) in previewModel.cumulativeInfoList" :key="`cum-${index}`">
              <div class="register-benefit-preview-panel__field">
                <div class="register-benefit-preview-panel__field-label">累计险种</div>
                <div class="register-benefit-preview-panel__field-value">
                  {{ formatInsutype(row.insutype) }}
                </div>
              </div>
              <div class="register-benefit-preview-panel__field">
                <div class="register-benefit-preview-panel__field-label">累计年月</div>
                <div class="register-benefit-preview-panel__field-value">
                  {{ displayBenefitDash(row.cumYm) }}
                </div>
              </div>
              <div
                class="register-benefit-preview-panel__field register-benefit-preview-panel__field--span-2"
              >
                <div class="register-benefit-preview-panel__field-label">累计类别</div>
                <div class="register-benefit-preview-panel__field-value">
                  {{ formatCumTypeCode(row.cumTypeCode) }}
                </div>
              </div>
              <div class="register-benefit-preview-panel__field">
                <div class="register-benefit-preview-panel__field-label">累计值</div>
                <div class="register-benefit-preview-panel__field-value">
                  {{ displayBenefitDash(row.cum) }}
                </div>
              </div>
            </template>
          </div>
          <div v-else class="register-benefit-preview-panel__group-empty">
            <ElTag v-if="panelTreatmentLoading" type="info" size="small">加载中</ElTag>
          </div>
        </div>
      </div>

      <ElAlert
        v-if="previewModel.treatmentBlocked"
        type="warning"
        :closable="false"
        show-icon
        title="存在基金项不享受待遇，请核对后再继续；系统默认不阻断。"
        class="register-benefit-preview-panel__alert"
      />
    </template>

    <template v-else>
      <div class="space-y-2">
        <ElDescriptions :column="3" size="small" border class="benefit-descriptions">
          <ElDescriptionsItem label="姓名">
            {{ displayBenefitDash(previewModel.psnName) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="证件号">
            <span class="break-all">{{ displayBenefitDash(previewModel.certno) }}</span>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="人员编号">
            {{ displayBenefitDash(previewModel.psnNo) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="性别">
            {{ formatBenefitGend(previewModel.gend) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="出生日期">
            {{ displayBenefitDash(previewModel.brdy) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem :label="benefitCredentialLabel(mediumType)">
            {{ displayBenefitDash(cardNo) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="参保地">
            {{
              formatInsuplcAdmdvsDisplay(
                selectedInsuRow?.insuplcAdmdvs ?? previewModel.insuplcAdmdvs,
              )
            }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="险种">
            {{ formatInsutype(selectedInsuRow?.insutype ?? previewModel.insutype) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="个账余额">
            <span class="text-orange-600">
              {{ formatBenefitBalc(selectedInsuRow?.balc ?? previewModel.balc) }}
            </span>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="人员类别">
            {{ formatBenefitPsnType(selectedInsuRow?.psnType) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="参保状态">
            {{ formatBenefitPsnInsuStas(selectedInsuRow?.psnInsuStas) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="参保日期">
            {{ displayBenefitDash(selectedInsuRow?.psnInsuDate) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="公务员">
            {{ formatCvlservFlag(selectedInsuRow?.cvlservFlag) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="就医地">
            {{ formatInsuplcAdmdvsDisplay(previewModel.areaHint?.mdtrtareaAdmdvs) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="单位名称" :span="3">
            {{ displayBenefitDash(selectedInsuRow?.empName) }}
          </ElDescriptionsItem>
        </ElDescriptions>

        <div v-if="(previewModel.insuinfoList?.length ?? 0) > 1">
          <div class="mb-1 text-gray-500">多险种参保（点击切换）</div>
          <ElTable
            :data="previewModel.insuinfoList"
            size="small"
            highlight-current-row
            :max-height="tableMaxHeight"
            @row-click="handleInsuRowClick"
          >
            <ElTableColumn label="险种" min-width="140">
              <template #default="{ row }">
                {{ formatInsutype(row.insutype) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="参保地" width="120">
              <template #default="{ row }">
                {{ formatInsuplcAdmdvsDisplay(row.insuplcAdmdvs) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="人员类别" min-width="100">
              <template #default="{ row }">
                {{ formatBenefitPsnType(row.psnType) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="余额" width="96">
              <template #default="{ row }">
                {{ formatBenefitBalc(row.balc) }}
              </template>
            </ElTableColumn>
            <ElTableColumn prop="empName" label="单位" min-width="120" show-overflow-tooltip />
          </ElTable>
        </div>

        <div>
          <div class="mb-1 flex flex-wrap items-center justify-between gap-2 text-gray-500">
            <div class="flex items-center gap-2">
              <span>{{ treatmentCheckTitle }}</span>
              <ElTag v-if="panelTreatmentLoading" type="info" size="small">查询中</ElTag>
            </div>
            <div
              v-if="notEnjoyedTreatmentCount > 0"
              class="flex items-center gap-2 text-xs text-gray-500"
            >
              <span>显示不享受待遇（{{ notEnjoyedTreatmentCount }}）</span>
              <ElSwitch v-model="showNotEnjoyedTreatment" size="small" />
            </div>
          </div>
          <ElAlert
            v-if="previewModel.treatmentBlocked"
            type="warning"
            :closable="false"
            show-icon
            title="存在基金项不享受待遇，请核对后再继续；系统默认不阻断。"
            class="mb-2"
          />
          <ElTable
            v-loading="panelTreatmentLoading"
            :data="displayedTreatmentRows"
            size="small"
            :max-height="tableMaxHeight"
            :empty-text="treatmentTableEmptyText"
          >
            <ElTableColumn label="基金类型" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                {{ formatFundPayType(row.fundPayType) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="享受标志" width="88">
              <template #default="{ row }">
                <ElTag :type="row.trtEnjymntFlag === '1' ? 'success' : 'warning'" size="small">
                  {{ formatTreatmentFlag(row.trtEnjymntFlag) }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="begndate" label="开始" width="100" />
            <ElTableColumn prop="enddate" label="结束" width="100" />
            <ElTableColumn
              prop="trtChkRslt"
              label="不享受原因"
              min-width="160"
              show-overflow-tooltip
            />
          </ElTable>
        </div>

        <div>
          <div class="mb-1 text-gray-500">慢特病备案</div>
          <ElTable
            v-loading="panelTreatmentLoading"
            :data="previewModel.opspFilingList ?? []"
            size="small"
            :max-height="opspTableMaxHeight"
            :empty-text="panelTreatmentLoading ? '正在加载…' : '暂无慢特病备案'"
          >
            <ElTableColumn
              prop="opspDiseName"
              label="病种名称"
              min-width="160"
              show-overflow-tooltip
            />
            <ElTableColumn prop="begndate" label="开始日期" width="100" />
            <ElTableColumn prop="enddate" label="结束日期" width="100" />
          </ElTable>
        </div>

        <div>
          <div class="mb-1 text-gray-500">费用累计（当月）</div>
          <ElTable
            v-loading="panelTreatmentLoading"
            :data="previewModel.cumulativeInfoList ?? []"
            size="small"
            :max-height="opspTableMaxHeight"
            :empty-text="panelTreatmentLoading ? '正在加载…' : '暂无累计信息'"
          >
            <ElTableColumn label="险种" min-width="140">
              <template #default="{ row }">
                {{ formatInsutype(row.insutype) }}
              </template>
            </ElTableColumn>
            <ElTableColumn prop="year" label="年度" width="64" />
            <ElTableColumn prop="cumYm" label="累计年月" width="88" />
            <ElTableColumn label="累计类别" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                {{ formatCumTypeCode(row.cumTypeCode) }}
              </template>
            </ElTableColumn>
            <ElTableColumn prop="cum" label="累计值" width="88" />
          </ElTable>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.register-benefit-preview-panel--vertical {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.register-benefit-preview-panel__alert {
  margin: 0;
}

.register-benefit-preview-panel__insu-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.register-benefit-preview-panel__insu-chip {
  padding: 2px 10px;
  font-size: 12px;
  line-height: 22px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  background: #fff;
  border: 1px solid #dce3eb;
  border-radius: 6px;
}

.register-benefit-preview-panel__insu-chip.is-active {
  color: var(--el-color-primary);
  background: #f8fbff;
  border-color: var(--el-color-primary);
}

.register-benefit-preview-panel__hero {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  background: linear-gradient(135deg, #f5f8fc 0%, #eef4fb 100%);
  border: 1px solid #e2eaf3;
  border-radius: 8px;
}

.register-benefit-preview-panel__hero-main {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  align-items: baseline;
  min-width: 0;
}

.register-benefit-preview-panel__hero-name {
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
  color: var(--el-text-color-primary);
}

.register-benefit-preview-panel__hero-meta {
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}

.register-benefit-preview-panel__hero-insu {
  padding: 0 8px;
  font-size: 12px;
  line-height: 20px;
  color: var(--el-color-primary);
  background: #fff;
  border: 1px solid color-mix(in srgb, var(--el-color-primary) 28%, transparent);
  border-radius: 999px;
}

.register-benefit-preview-panel__hero-balc {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  gap: 2px;
  align-items: flex-end;
}

.register-benefit-preview-panel__hero-balc-label {
  font-size: 11px;
  line-height: 16px;
  color: var(--el-text-color-secondary);
}

.register-benefit-preview-panel__hero-balc-value {
  font-size: 15px;
  font-weight: 600;
  line-height: 22px;
  color: #c2410c;
  font-variant-numeric: tabular-nums;
}

.register-benefit-preview-panel__groups {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.register-benefit-preview-panel__group {
  padding: 8px 10px;
  background: var(--el-fill-color-blank, #fff);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.register-benefit-preview-panel__group-title {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 600;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}

.register-benefit-preview-panel__group-empty {
  font-size: 12px;
  line-height: 20px;
  color: var(--el-text-color-placeholder);
}

.register-benefit-preview-panel__field-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 4px 12px;
  align-items: start;
}

.register-benefit-preview-panel__field {
  display: flex;
  flex-direction: row;
  gap: 6px;
  align-items: baseline;
  min-width: 0;
}

.register-benefit-preview-panel__field--span-2 {
  grid-column: span 2;
}

.register-benefit-preview-panel__field--span-3 {
  grid-column: span 3;
}

.register-benefit-preview-panel__field--span-4 {
  grid-column: span 3;
}

.register-benefit-preview-panel__field-label {
  flex-shrink: 0;
  width: 56px;
  font-size: 12px;
  font-weight: 500;
  line-height: 20px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.register-benefit-preview-panel__field-label::after {
  content: '：';
}

.register-benefit-preview-panel__field-value {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  line-height: 20px;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.register-benefit-preview-panel__field-value--emphasis {
  font-weight: 600;
  color: var(--el-color-primary);
}

.register-benefit-preview-panel__field-value--accent {
  font-weight: 600;
  color: #c2410c;
}

.benefit-descriptions :deep(.el-descriptions__label) {
  width: 88px;
  color: #64748b;
  font-weight: 400;
}

.benefit-descriptions :deep(.el-descriptions__content) {
  color: #0f172a;
  font-weight: 500;
}
</style>
