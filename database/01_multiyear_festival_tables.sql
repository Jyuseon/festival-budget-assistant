-- =========================================================
-- 다년도(2017~2026) 지역축제 sanitized CSV Import용 테이블
-- 실행: mysql -u root -p festival_budget < 01_multiyear_festival_tables.sql
--
-- 개발 환경(application-local.yml)은 spring.jpa.hibernate.ddl-auto=update 라서
-- MultiYearFestivalRecord/MultiYearImportBatch 엔티티만 추가해도 이 테이블들은 서버
-- 기동 시 자동 생성된다. 이 스크립트는 그 결과와 동일한 스키마를 운영/검증 환경에서도
-- 재현할 수 있도록 문서화한 것이다(ddl-auto=validate/none 전환 시 사용).
--
-- 기존 2026 전용 festival_record / dataset_import_batch 테이블과는 완전히 분리된
-- 별도 테이블이며, 이 스크립트는 기존 테이블을 전혀 건드리지 않는다.
-- =========================================================

USE festival_budget;

CREATE TABLE IF NOT EXISTS multi_year_import_batch (
    id                                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_file_name                   VARCHAR(512) NOT NULL,
    file_hash                            VARCHAR(64)  NOT NULL,
    dataset_years                        VARCHAR(100),
    total_rows                           INT NOT NULL,
    unit_scale_suspect_rows              INT NOT NULL,
    missing_or_nonpositive_budget_rows   INT NOT NULL,
    missing_duration_rows                INT NOT NULL,
    covid_affected_rows                  INT NOT NULL,
    imported_at                          DATETIME(6) NOT NULL,
    status                               VARCHAR(20) NOT NULL,
    error_summary                        LONGTEXT,
    CONSTRAINT uk_multi_year_import_batch_file_hash UNIQUE (file_hash)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS multi_year_festival_record (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- 원본 추적
    dataset_year              INT NOT NULL,
    source_sheet              VARCHAR(100),
    source_row                INT,
    source_sha256             VARCHAR(64),

    -- 지역
    region_raw                VARCHAR(100),
    region_text               VARCHAR(50),
    region_code               VARCHAR(20),
    district_raw              VARCHAR(100),
    district_text             VARCHAR(100),

    -- 축제명/유형
    festival_name             VARCHAR(255) NOT NULL,
    festival_type_raw         VARCHAR(255),
    festival_type             VARCHAR(255),

    -- 개최 장소
    venue_name_raw            VARCHAR(500),
    venue_type_raw            VARCHAR(100),
    venue_type                VARCHAR(20),

    -- 개최 기간
    period_raw                VARCHAR(255),
    duration_days             INT,
    duration_source           VARCHAR(20),
    duration_note_raw         VARCHAR(255),

    -- 개최 주기 / 진행 상태
    cycle_raw                 VARCHAR(255),
    event_mode_raw            VARCHAR(255),
    event_status_raw          VARCHAR(255),
    covid_affected            TINYINT(1) NOT NULL,
    first_held_year           INT,

    -- 예산 (원본 단위 백만원 그대로 보존)
    budget_total_raw          VARCHAR(100),
    budget_total_million      DECIMAL(18,3),
    budget_national_million   DECIMAL(18,3),
    budget_local_million      DECIMAL(18,3),
    budget_other_million      DECIMAL(18,3),
    budget_quality_flag       VARCHAR(30) NOT NULL,
    budget_quality_note       VARCHAR(255),

    -- 방문객
    visitor_total_persons     BIGINT,

    -- Import 메타데이터
    import_batch_id           BIGINT NOT NULL,
    created_at                DATETIME(6) NOT NULL,

    CONSTRAINT fk_myfr_import_batch FOREIGN KEY (import_batch_id)
        REFERENCES multi_year_import_batch (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE INDEX idx_myfr_dataset_year ON multi_year_festival_record (dataset_year);
CREATE INDEX idx_myfr_import_batch ON multi_year_festival_record (import_batch_id);
CREATE INDEX idx_myfr_source ON multi_year_festival_record (source_sha256, source_sheet, source_row);

SELECT '다년도 CSV Import 테이블 생성 완료' AS result;