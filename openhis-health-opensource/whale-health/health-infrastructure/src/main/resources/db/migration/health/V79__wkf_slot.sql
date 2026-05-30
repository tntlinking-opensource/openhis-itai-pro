-- wkf_slot（建表）
-- Flyway V79

CREATE TABLE IF NOT EXISTS "public"."wkf_slot" (
    "id" int8 NOT NULL,
    "tenant_id" int8,
    "created_at" timestamptz(6) NOT NULL,
    "updated_at" timestamptz(6),
    "created_by" int8,
    "updated_by" int8,
    "is_deleted" bool NOT NULL DEFAULT false,
    "deleted_at" timestamptz(6),
    "deleted_by" int8,
    "version" int8 NOT NULL DEFAULT 0,
    "organization_id" int8 NOT NULL,
    "schedule_id" int8 NOT NULL,
    "start_time" timestamptz(6) NOT NULL,
    "end_time" timestamptz(6) NOT NULL,
    "status_code" varchar(255) NOT NULL,
    "capacity" int4 NOT NULL,
    "booked_count" int4 NOT NULL,
    "overbooked_flag" bool NOT NULL,
    "service_category_json" varchar(255),
    "service_type_json" varchar(255),
    "specialty_json" varchar(255),
    "appointment_type_json" varchar(255),
    "comment_text" varchar(255),
    CONSTRAINT "wkf_slot_pkey" PRIMARY KEY ("id")
);