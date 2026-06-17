/**
 * 科室数据类型
 */
export interface Department {
  id: number | string;
  name?: string;
  displayName?: string;
  code?: string;
  typeEnum?: number | string;
  typeEnum_dictText?: string;
  classEnum?: number | string;
  classEnum_dictText?: string;
  ybNo?: string;
  ybName?: string;
  medinsId?: string;
  medinsAdmdvs?: string;
  medinsType?: string;
  medinsLv?: string;
  defDoctorId?: number | string;
  caty?: string;
  statusEnum?: string;
  statusEnum_dictText?: string;
  busNo?: string;
  parentId?: null | string;
  parentDisplayName?: null | string;
  parentOrganization?: null | Department;
  parentDepartment?: null | Department;
  pyStr?: null | string;
  wbStr?: null | string;
  displayOrder?: number;
  sortOrder?: number;
  children?: Department[];
  isEnabled?: boolean;
  remark?: null | string;
  tenantId?: number | string;
  createdAt?: string;
  updatedAt?: null | string;
}

export interface DepartmentOption {
  label: string;
  value: string;
  sort?: number;
  children?: null | DepartmentOption[];
}

export interface PractitionerOption {
  label: string;
  value: string;
  sort?: number;
}

export interface DepartmentClassOption {
  label: string;
  value: string;
  sort?: number;
}

export interface DepartmentTypeOption {
  label: string;
  value: string;
  sort?: number;
}

export interface InitDepartmentResponse {
  departmentClassOptions: DepartmentClassOption[];
  departmentTypeOptions: DepartmentTypeOption[];
  departmentOptions: DepartmentOption[];
  practitionerOptions: PractitionerOption[];
}

export interface QueryDepartmentListParams {
  keyword?: string;
  typeEnum?: number | string;
  classEnum?: number | string;
  statusEnum?: string;
}

export interface DepartmentParams {
  id?: number | string;
  name: string;
  typeEnum?: number | string;
  classEnum?: number | string;
  ybNo?: string;
  ybName?: string;
  medinsId?: string;
  medinsAdmdvs?: string;
  medinsType?: string;
  medinsLv?: string;
  defDoctorId?: number | string;
  caty?: string;
  activeFlag?: number;
  parentId?: string;
  parentIdChanged?: boolean;
  busNo?: string;
  pyStr?: string;
  wbStr?: string;
  displayOrder?: number;
  [key: string]: unknown;
}

export type DepartmentTree = Department;
