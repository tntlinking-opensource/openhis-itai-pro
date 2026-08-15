<script setup lang="ts">
import type { NurseStationModulePatient } from '../../components/nurse-station-module-patient.types';
import type {
  AutoRollConfigFormModel,
  AutoRollConfigTableRow,
  AutoRollSelectedCatalogItem,
} from '../services/map-nurse-station-auto-roll';

import type { NurseStationPricingCatalogRowDto } from '#/api/inpatient/nurse-station/inpatient-nurse-station-inpatient-billing.types';

import { computed, reactive, ref, watch } from 'vue';

import { ElMessage } from 'element-plus';

import { httpPageNurseStationPricingCatalog } from '#/api/inpatient/nurse-station/inpatient-nurse-station-inpatient-billing.http';
import AppDialog from '#/components/app-dialog.vue';
import { useRequest } from '#/hooks/use-request';
import { useUserStore } from '@vben/stores';

import { useAuthStore } from '#/store';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
import {
  preserveApiIdForRequest,
  resolveLoginPractitionerIdForRequest,
} from '#/utils/format/preserve-api-id';

import { PRICING_PRODUCT_TABLE_SERVICE } from '../services/map-nurse-station-inpatient-billing';
import {
  AUTO_ROLL_DEFAULT_BUSINESS_TIME,
  buildAutoRollCatalogFromTableRow,
  buildAutoRollCatalogRowKey,
  buildAutoRollFormFromTableRow,
  isAutoRollConfigurableCatalogRow,
} from '../services/map-nurse-station-auto-roll';

defineOptions({
  name: 'InpatientBillingAutoRollConfigDialog',
});

const props = withDefaults(defineProps<Props>(), {
  canOperate: false,
  editRow: null,
  nurseOptions: () => [],
  patient: null,
  saving: false,
  visible: false,
});

const emit = defineEmits<{
  closed: [];
  saved: [
    payload: {
      form: AutoRollConfigFormModel;
      selectedCatalog: AutoRollSelectedCatalogItem;
      mode: 'create' | 'edit';
      ruleId?: string;
    },
  ];
  'update:visible': [value: boolean];
}>();

interface Props {
  canOperate?: boolean;
  editRow?: AutoRollConfigTableRow | null;
  /** 计费人护士下拉 */
  nurseOptions?: { label: string; value: string }[];
  patient?: NurseStationModulePatient | null;
  saving?: boolean;
  visible?: boolean;
}

const CATALOG_PAGE_SIZE = 20;

const catalogKeyword = ref('');
const catalogPageNo = ref(1);
const catalogTotal = ref(0);
const catalogRows = ref<NurseStationPricingCatalogRowDto[]>([]);
const selectedCatalog = ref<AutoRollSelectedCatalogItem | null>(null);
const formModel = reactive<AutoRollConfigFormModel>({
  quantityValue: 1,
  remark: '',
  businessTime: AUTO_ROLL_DEFAULT_BUSINESS_TIME,
  billingPractitionerId: '',
});

const catalogRequest = useRequest(httpPageNurseStationPricingCatalog, { manual: true });
const authStore = useAuthStore();
const userStore = useUserStore();

const visibleModel = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
});

/** 弹窗是否为编辑模式。 */
const isEditMode = computed(() => props.editRow != null);

/** 弹窗标题文案。 */
const dialogTitle = computed(() => (isEditMode.value ? '编辑自动计费配置' : '新增自动计费配置'));

/** 当前选中的诊疗项目展示名。 */
const selectedCatalogName = computed(() => selectedCatalog.value?.catalog.name?.trim() || '—');

/** 当前选中的诊疗项目价格。 */
const selectedCatalogPrice = computed(() => {
  const raw = selectedCatalog.value?.catalog.unitPrice;
  if (raw === undefined || raw === null || raw === '') {
    return '—';
  }
  const num = Number(raw);
  if (!Number.isFinite(num)) {
    return '—';
  }
  return `￥${formatMoneyDisplay(num)}`;
});

/** 计费人下拉（保证编辑回显项在内）。 */
const billingPractitionerSelectOptions = computed(() => {
  const merged = [...props.nurseOptions];
  const currentId = formModel.billingPractitionerId;
  if (!currentId) {
    return merged;
  }
  // 已选计费人若不在护士列表中，补一条避免回显空白
  if (!merged.some((opt) => opt.value === currentId)) {
    const name = props.editRow?.billingPractitionerName?.trim();
    merged.unshift({
      value: currentId,
      label: name && name !== '—' ? name : currentId,
    });
  }
  return merged;
});

/** 取当前登录机构，供选品目录查询。 */
function resolveOrganizationUnitId(): string | undefined {
  const orgRaw = authStore.getSelectedOrganization();
  return preserveApiIdForRequest(orgRaw) ?? undefined;
}

/** 重置弹窗表单与选品状态。 */
function resetDialogState() {
  catalogKeyword.value = '';
  catalogPageNo.value = 1;
  catalogTotal.value = 0;
  catalogRows.value = [];
  selectedCatalog.value = null;
  formModel.quantityValue = 1;
  formModel.remark = '';
  formModel.businessTime = AUTO_ROLL_DEFAULT_BUSINESS_TIME;
  formModel.billingPractitionerId = resolveLoginPractitionerIdForRequest(userStore.userInfo) ?? '';
}

/** 编辑模式下用表格行预填表单与选品。 */
function applyEditRow(row: AutoRollConfigTableRow) {
  const form = buildAutoRollFormFromTableRow(row);
  formModel.quantityValue = form.quantityValue;
  formModel.remark = form.remark;
  formModel.businessTime = form.businessTime;
  formModel.billingPractitionerId = form.billingPractitionerId;
  selectedCatalog.value = buildAutoRollCatalogFromTableRow(row);
}

/** 加载诊疗项目选品目录（仅诊疗表；护理/转科/出院/嘱托前端再滤一层）。 */
async function loadCatalog() {
  const patient = props.patient;
  const encounterId = patient ? preserveApiIdForRequest(patient.id) : undefined;
  const patientId = patient ? preserveApiIdForRequest(patient.patientId) : undefined;
  const res = await catalogRequest.run({
    encounterId,
    patientId,
    keyword: catalogKeyword.value.trim() || undefined,
    pageNo: catalogPageNo.value,
    pageSize: CATALOG_PAGE_SIZE,
    productTableNames: [PRICING_PRODUCT_TABLE_SERVICE],
    organizationUnitId: resolveOrganizationUnitId(),
  });
  const parsed = parsePagedRowsFromApiData<NurseStationPricingCatalogRowDto>(res);
  // 不计费/护理类目不展示，避免保存时被后端拒绝
  catalogRows.value = parsed.content.filter((row) => isAutoRollConfigurableCatalogRow(row));
  catalogTotal.value = parsed.totalCount;
}

/** 弹窗打开时初始化并加载选品。 */
async function handleDialogOpen() {
  resetDialogState();
  if (props.editRow) {
    applyEditRow(props.editRow);
  }
  if (!props.canOperate) {
    return;
  }
  await loadCatalog();
}

/** 点击目录行选中诊疗项目。 */
function handleCatalogRowClick(row: NurseStationPricingCatalogRowDto) {
  if (!isAutoRollConfigurableCatalogRow(row)) {
    ElMessage.warning('护理、转科、出院、嘱托等不计费项目不能配置自动计费');
    return;
  }
  const rowKey = buildAutoRollCatalogRowKey(row);
  selectedCatalog.value = { rowKey, catalog: row };
}

/** 校验并提交表单。 */
function handleSubmit() {
  if (!props.canOperate) {
    ElMessage.warning('住院记账仅支持单选患者，请在左侧只勾选 1 位患者');
    return;
  }
  if (!selectedCatalog.value) {
    ElMessage.warning('请先在左侧选择诊疗项目');
    return;
  }
  const activityId = preserveApiIdForRequest(
    selectedCatalog.value.catalog.activityId ?? selectedCatalog.value.catalog.productId,
  );
  const chargeDefinitionId = preserveApiIdForRequest(
    selectedCatalog.value.catalog.chargeItemDefinitionId,
  );
  if (!activityId || !chargeDefinitionId) {
    ElMessage.warning('所选诊疗项目缺少活动或定价信息，请重新选择');
    return;
  }
  if (!formModel.quantityValue || formModel.quantityValue <= 0) {
    ElMessage.warning('数量必须大于 0');
    return;
  }
  if (!formModel.businessTime?.trim()) {
    ElMessage.warning('请选择业务时间');
    return;
  }
  const billingPractitionerId = preserveApiIdForRequest(formModel.billingPractitionerId);
  if (!billingPractitionerId) {
    ElMessage.warning('请选择计费人');
    return;
  }
  emit('saved', {
    form: {
      quantityValue: formModel.quantityValue,
      remark: formModel.remark.trim(),
      businessTime: formModel.businessTime.trim(),
      billingPractitionerId,
    },
    selectedCatalog: selectedCatalog.value,
    mode: isEditMode.value ? 'edit' : 'create',
    ruleId: props.editRow?.ruleId,
  });
}

/** 目录翻页变更。 */
function handleCatalogPageChange(page: number) {
  catalogPageNo.value = page;
  void loadCatalog();
}

/** 目录关键字查询。 */
function handleCatalogSearch() {
  catalogPageNo.value = 1;
  void loadCatalog();
}

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      void handleDialogOpen();
    }
  },
);
</script>

<template>
  <AppDialog
    v-model="visibleModel"
    class="auto-roll-config-dialog-wrapper"
    :title="dialogTitle"
    width="980px"
    @closed="emit('closed')"
  >
    <div class="auto-roll-config-dialog">
      <ElAlert
        v-if="!canOperate"
        title="当前不是单选患者状态，请先在左侧只勾选 1 位患者"
        type="warning"
        :closable="false"
        class="w-full"
      />

      <div class="auto-roll-config-dialog__body">
        <section class="auto-roll-config-panel auto-roll-config-panel--left">
          <header class="auto-roll-config-panel__header">诊疗项目选品</header>
          <div class="auto-roll-config-filter">
            <ElInput
              v-model="catalogKeyword"
              clearable
              placeholder="编码/名称"
              class="auto-roll-config-filter__keyword"
              @keyup.enter="handleCatalogSearch"
            />
            <ElButton
              type="primary"
              :loading="catalogRequest.loading.value"
              @click="handleCatalogSearch"
            >
              查询
            </ElButton>
          </div>
          <ElTable
            v-loading="catalogRequest.loading.value"
            :data="catalogRows"
            border
            height="100%"
            highlight-current-row
            :row-key="(row: NurseStationPricingCatalogRowDto) => buildAutoRollCatalogRowKey(row)"
            :current-row-key="selectedCatalog?.rowKey"
            @row-click="handleCatalogRowClick"
          >
            <ElTableColumn prop="busNo" label="编码" min-width="92" />
            <ElTableColumn prop="name" label="项目名称" min-width="160" show-overflow-tooltip />
            <ElTableColumn label="价格" min-width="88" align="right">
              <template #default="{ row }">
                {{
                  row.unitPrice != null && row.unitPrice !== ''
                    ? `￥${formatMoneyDisplay(Number(row.unitPrice))}`
                    : '—'
                }}
              </template>
            </ElTableColumn>
            <ElTableColumn prop="unitName" label="单位" width="60" />
          </ElTable>
          <div class="auto-roll-config-panel__pager">
            <ElPagination
              v-model:current-page="catalogPageNo"
              :page-size="CATALOG_PAGE_SIZE"
              :total="catalogTotal"
              layout="total, prev, pager, next"
              small
              @current-change="handleCatalogPageChange"
            />
          </div>
        </section>

        <section class="auto-roll-config-panel auto-roll-config-panel--right">
          <header class="auto-roll-config-panel__header">配置信息</header>
          <ElForm label-width="72px" class="auto-roll-config-form">
            <ElFormItem label="项目名称" required>
              <span class="auto-roll-config-form__readonly">{{ selectedCatalogName }}</span>
            </ElFormItem>
            <ElFormItem label="价格">
              <span class="auto-roll-config-form__readonly">{{ selectedCatalogPrice }}</span>
            </ElFormItem>
            <ElFormItem label="数量" required>
              <ElInputNumber
                v-model="formModel.quantityValue"
                :min="1"
                :step="1"
                :controls="false"
                class="auto-roll-config-form__qty"
              />
            </ElFormItem>
            <ElFormItem label="业务时间" required>
              <ElTimePicker
                v-model="formModel.businessTime"
                value-format="HH:mm:ss"
                format="HH:mm:ss"
                placeholder="默认 23:59:59"
                class="w-full"
              />
            </ElFormItem>
            <ElFormItem label="计费人" required>
              <ElSelect
                v-model="formModel.billingPractitionerId"
                filterable
                clearable
                placeholder="请选择计费人"
                class="w-full"
              >
                <ElOption
                  v-for="opt in billingPractitionerSelectOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="备注">
              <ElInput
                v-model="formModel.remark"
                type="textarea"
                :rows="3"
                maxlength="200"
                placeholder="可选"
              />
            </ElFormItem>
          </ElForm>
        </section>
      </div>
    </div>

    <template #footer>
      <ElButton @click="visibleModel = false">取消</ElButton>
      <ElButton type="primary" :loading="saving" :disabled="!canOperate" @click="handleSubmit">
        保存
      </ElButton>
    </template>
  </AppDialog>
</template>

<style scoped>
.auto-roll-config-dialog-wrapper {
  max-width: calc(100vw - 48px);
}

.auto-roll-config-dialog {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.auto-roll-config-dialog__body {
  display: grid;
  gap: 16px;
  grid-template-columns: minmax(420px, 1fr) minmax(320px, 0.85fr);
  height: 420px;
}

.auto-roll-config-panel {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.auto-roll-config-panel__header {
  background: var(--el-fill-color-light);
  border-bottom: 1px solid var(--el-border-color-light);
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
  min-height: 42px;
  padding: 0 10px;
  display: flex;
  align-items: center;
}

.auto-roll-config-filter {
  align-items: center;
  display: flex;
  gap: 8px;
  min-height: 52px;
  padding: 8px 10px;
}

.auto-roll-config-filter__keyword {
  flex: 1;
}

.auto-roll-config-panel__pager {
  display: flex;
  justify-content: flex-end;
  padding: 8px 10px 0;
}

.auto-roll-config-form {
  padding: 12px 12px 0;
}

.auto-roll-config-form__readonly {
  color: var(--el-text-color-primary);
  font-size: 13px;
}

.auto-roll-config-form__qty {
  width: 120px;
}
</style>
