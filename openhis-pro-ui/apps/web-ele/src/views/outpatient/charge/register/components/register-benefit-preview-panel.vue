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
    ybCardInfo?: string;
    ybCardSn?: string;
    ybCertno?: string;
    ybMediumType?: string;
  }>(),
  {
    scrollableTables: true,
    medType: '11',
  },
);

const previewModel = ref<YbRegisterBenefitPreviewDto>({ ...props.preview });
const selectedInsuIndex = ref(0);
const treatmentLoading = ref(false);
/** 默认仅展示享受待遇的基金项 */
const showNotEnjoyedTreatment = ref(false);

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
  showNotEnjoyedTreatment.value = false;
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

defineExpose({
  syncPreview,
});
</script>

<template>
  <div class="register-benefit-preview-panel space-y-2 text-sm">
    <ElAlert
      v-if="previewModel.areaHint?.hintMessage"
      :type="areaAlertType"
      :closable="false"
      show-icon
      :title="previewModel.areaHint.hintMessage"
    />

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
          formatInsuplcAdmdvsDisplay(selectedInsuRow?.insuplcAdmdvs ?? previewModel.insuplcAdmdvs)
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
        {{ displayBenefitDash(previewModel.areaHint?.mdtrtareaAdmdvs) }}
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
          <ElTag v-if="treatmentLoading" type="info" size="small">查询中</ElTag>
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
        v-loading="treatmentLoading"
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
        <ElTableColumn prop="trtChkRslt" label="不享受原因" min-width="160" show-overflow-tooltip />
      </ElTable>
    </div>

    <div>
      <div class="mb-1 text-gray-500">慢特病备案</div>
      <ElTable
        :data="previewModel.opspFilingList ?? []"
        size="small"
        :max-height="opspTableMaxHeight"
        empty-text="暂无慢特病备案"
      >
        <ElTableColumn prop="opspDiseName" label="病种名称" min-width="160" show-overflow-tooltip />
        <ElTableColumn prop="begndate" label="开始日期" width="100" />
        <ElTableColumn prop="enddate" label="结束日期" width="100" />
      </ElTable>
    </div>

    <div>
      <div class="mb-1 text-gray-500">费用累计（当月）</div>
      <ElTable
        :data="previewModel.cumulativeInfoList ?? []"
        size="small"
        :max-height="opspTableMaxHeight"
        empty-text="暂无累计信息"
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

<style scoped>
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
