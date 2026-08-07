-- =========================================================
-- festivalSeries 연결 분석용 테이블 (동일 축제의 연도별 반복 구조 파악)
-- 실행: mysql -u root -p festival_budget < 02_festival_series_tables.sql
--
-- 개발 환경은 ddl-auto=update라 엔티티만으로 자동 생성되지만, 이 스크립트는 그 결과와
-- 동일한 스키마를 운영/검증 환경에서도 재현할 수 있도록 문서화한 것이다.
--
-- 순수 분석 산출물이다 - festival_record/dataset_import_batch(2026 production)는 물론
-- multi_year_festival_record 원본 값도 전혀 건드리지 않는다. FestivalSeriesLinkingService를
-- 재실행하면 이 세 테이블만 지우고 다시 채운다.
-- =========================================================

USE festival_budget;

CREATE TABLE IF NOT EXISTS festival_series (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    canonical_name        VARCHAR(255) NOT NULL,
    canonical_region      VARCHAR(50),
    canonical_district    VARCHAR(100),
    first_observed_year   INT NOT NULL,
    last_observed_year    INT NOT NULL,
    record_count          INT NOT NULL,
    match_status          VARCHAR(20) NOT NULL,
    scope                 VARCHAR(20) NOT NULL,
    created_at            DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE INDEX idx_festival_series_canonical_name ON festival_series (canonical_name);
CREATE INDEX idx_festival_series_region ON festival_series (canonical_region);

CREATE TABLE IF NOT EXISTS festival_series_membership (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    festival_record_id    BIGINT NOT NULL,
    festival_series_id    BIGINT NOT NULL,
    match_method          VARCHAR(20) NOT NULL,
    match_score           DOUBLE,
    match_confidence      VARCHAR(10),
    created_at            DATETIME(6) NOT NULL,
    CONSTRAINT uk_fsm_festival_record UNIQUE (festival_record_id),
    CONSTRAINT fk_fsm_festival_record FOREIGN KEY (festival_record_id) REFERENCES multi_year_festival_record (id),
    CONSTRAINT fk_fsm_festival_series FOREIGN KEY (festival_series_id) REFERENCES festival_series (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE INDEX idx_fsm_series ON festival_series_membership (festival_series_id);
CREATE INDEX idx_fsm_match_method ON festival_series_membership (match_method);

CREATE TABLE IF NOT EXISTS festival_series_match_candidate (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_record_id        BIGINT NOT NULL,
    candidate_record_id     BIGINT NOT NULL,
    name_similarity         DOUBLE NOT NULL,
    district_signal         DOUBLE NOT NULL,
    year_adjacency_signal   DOUBLE NOT NULL,
    type_signal             DOUBLE NOT NULL,
    score                   DOUBLE NOT NULL,
    confidence_band         VARCHAR(10) NOT NULL,
    applied                 TINYINT(1) NOT NULL,
    created_at               DATETIME(6) NOT NULL,
    CONSTRAINT fk_fsmc_source_record FOREIGN KEY (source_record_id) REFERENCES multi_year_festival_record (id),
    CONSTRAINT fk_fsmc_candidate_record FOREIGN KEY (candidate_record_id) REFERENCES multi_year_festival_record (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE INDEX idx_fsmc_source_record ON festival_series_match_candidate (source_record_id);
CREATE INDEX idx_fsmc_confidence_band ON festival_series_match_candidate (confidence_band);

SELECT 'festivalSeries 연결 분석 테이블 생성 완료' AS result;