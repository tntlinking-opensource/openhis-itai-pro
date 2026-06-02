import type { InpatientDoctorStationPatient } from './doctorPatientStore';

import { ref } from 'vue';

import { ElMessage } from 'element-plus';

import { httpPageInpatientDoctorStationPatientQueue } from '#/api/inpatient/doctor-station/inpatient-doctor-station-encounter.http';
import { useRequest } from '#/hooks/use-request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

import { mapDoctorStationPatientQueueRow } from './services/map-inpatient-doctor-station-api';

const QUEUE_PAGE_SIZE = 50;

/**
 * 住院医生站在院患者队列（IF-IP-DOR-01）：关键字检索、分页拉全量、选中态维护入口。
 * 科室范围由后端登录上下文写入，请求体不传 organizationId。
 */
export function useInpatientDoctorPatientQueue() {
  const listKeyword = ref('');
  const inHospitalPatients = ref<InpatientDoctorStationPatient[]>([]);

  /** 按页拉取直至当前关键字条件下全部在院患者 */
  async function fetchAllQueueRows(): Promise<InpatientDoctorStationPatient[]> {
    const rows: InpatientDoctorStationPatient[] = [];
    let pageNo = 1;
    let totalCount = 0;
    while (true) {
      const res = await httpPageInpatientDoctorStationPatientQueue({
        keyword: listKeyword.value.trim() || undefined,
        pageNo,
        pageSize: QUEUE_PAGE_SIZE,
      });
      const parsed = parsePagedRowsFromApiData(res);
      for (const row of parsed.content) {
        rows.push(mapDoctorStationPatientQueueRow(row));
      }
      totalCount = parsed.totalCount;
      if (rows.length >= totalCount || parsed.content.length === 0) {
        break;
      }
      pageNo += 1;
    }
    return rows;
  }

  const { loading: listLoading, run: runLoadQueue } = useRequest(fetchAllQueueRows, {
    manual: true,
    onSuccess: (rows) => {
      inHospitalPatients.value = rows;
    },
  });

  /** 加载队列并处理默认选中（刷新时保留当前选中行） */
  async function loadQueue(options?: { skipDefaultSelection?: boolean }) {
    const rows = await runLoadQueue();
    if (!rows) {
      return;
    }
    return { rows, skipDefaultSelection: options?.skipDefaultSelection };
  }

  /** 进入页面前置：需已选登录科室（与后端 EX-IP-DOR-102 口径一致的前端提示） */
  function assertOrganizationSelected(getOrganizationId: () => number): boolean {
    const organizationId = getOrganizationId();
    if (!organizationId || Number.isNaN(organizationId)) {
      ElMessage.warning('请先选择科室');
      inHospitalPatients.value = [];
      return false;
    }
    return true;
  }

  return {
    assertOrganizationSelected,
    inHospitalPatients,
    listKeyword,
    listLoading,
    loadQueue,
  };
}
