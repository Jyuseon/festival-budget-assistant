-- =========================================================
-- 축제 예산 추천 어시스트 - 초기 데이터베이스 생성 스크립트
-- 실행: mysql -u root -p < 00_init_database.sql
-- =========================================================

CREATE DATABASE IF NOT EXISTS festival_budget
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

-- 개발 전용 계정이 필요하면 아래 주석을 해제해서 사용 (현재는 root 계정 사용)
-- CREATE USER IF NOT EXISTS 'festival_dev'@'localhost' IDENTIFIED BY 'devpass';
-- GRANT ALL PRIVILEGES ON festival_budget.* TO 'festival_dev'@'localhost';
-- FLUSH PRIVILEGES;

USE festival_budget;

SELECT '데이터베이스 festival_budget 생성 완료' AS result;