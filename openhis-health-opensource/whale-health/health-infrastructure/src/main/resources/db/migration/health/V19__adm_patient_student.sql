-- adm_patient_student（建表）
-- Flyway V19

CREATE TABLE IF NOT EXISTS "public"."adm_patient_student" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int4 NOT NULL DEFAULT 0,
    "name" varchar(255) NOT NULL,
    "py_str" varchar(255),
    "wb_str" varchar(255),
    "gender_enum" varchar(255),
    "age" int4,
    "student_no" varchar(255) NOT NULL,
    "id_card" varchar(255),
    "phone" varchar(255),
    "college" varchar(255),
    "major" varchar(255),
    "edu_level" int4,
    "enroll_time" timestamptz(6),
    "leave_time" timestamptz(6),
    "grade" varchar(255),
    "study_type" int4,
    "school_status" int4,
    "physical_exam_result" int4,
    "counselor" varchar(255),
    "counselor_phone" varchar(255),
    "patient_id" int8 NOT NULL,
    CONSTRAINT "adm_patient_student_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE adm_patient_student IS '管理域 patient student';

COMMENT ON COLUMN adm_patient_student.id IS '主键';
COMMENT ON COLUMN adm_patient_student.tenant_id IS '租户标识';
COMMENT ON COLUMN adm_patient_student.created_at IS '创建时间';
COMMENT ON COLUMN adm_patient_student.updated_at IS '最后更新时间';
COMMENT ON COLUMN adm_patient_student.created_by IS '创建人';
COMMENT ON COLUMN adm_patient_student.updated_by IS '最后更新人';
COMMENT ON COLUMN adm_patient_student.is_deleted IS '软删标记';
COMMENT ON COLUMN adm_patient_student.deleted_at IS '删除时间';
COMMENT ON COLUMN adm_patient_student.deleted_by IS '删除人';
COMMENT ON COLUMN adm_patient_student.version IS '乐观锁版本号';
COMMENT ON COLUMN adm_patient_student.name IS '公费学生资质子实体';
COMMENT ON COLUMN adm_patient_student.py_str IS '拼音码';
COMMENT ON COLUMN adm_patient_student.wb_str IS '五笔码';
COMMENT ON COLUMN adm_patient_student.gender_enum IS '性别编码';
COMMENT ON COLUMN adm_patient_student.age IS '年龄';
COMMENT ON COLUMN adm_patient_student.student_no IS '学号';
COMMENT ON COLUMN adm_patient_student.id_card IS '身份证号';
COMMENT ON COLUMN adm_patient_student.phone IS '联系电话';
COMMENT ON COLUMN adm_patient_student.college IS '学院';
COMMENT ON COLUMN adm_patient_student.major IS '专业';
COMMENT ON COLUMN adm_patient_student.edu_level IS '学历层次';
COMMENT ON COLUMN adm_patient_student.enroll_time IS '入校时间';
COMMENT ON COLUMN adm_patient_student.leave_time IS '离校时间';
COMMENT ON COLUMN adm_patient_student.grade IS '年级';
COMMENT ON COLUMN adm_patient_student.study_type IS '学习形式';
COMMENT ON COLUMN adm_patient_student.school_status IS '在校状态';
COMMENT ON COLUMN adm_patient_student.physical_exam_result IS '体检结果';
COMMENT ON COLUMN adm_patient_student.counselor IS '辅导员';
COMMENT ON COLUMN adm_patient_student.counselor_phone IS '辅导员电话';
COMMENT ON COLUMN adm_patient_student.patient_id IS '所属患者标识';

