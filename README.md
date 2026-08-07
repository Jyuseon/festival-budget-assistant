# 축제 예산 추천 어시스트

공공기관에서 사용하는 축제 예산 추천 알고리즘을 기반으로 한 축제 기획 어시스트 웹.
현재는 로컬 개발 환경 전용으로 구성되어 있으며, UI보다 기능 구현에 집중한다.

## 기술 스택

| 영역 | 기술 |
|---|---|
| Frontend | Next.js 16 (App Router, TypeScript, Tailwind CSS) |
| Backend | Spring Boot 4.1 (Java 21, Spring Web MVC, Spring Data JPA, Validation) |
| Database | MySQL 8.0 |
| Build | Maven (backend, mvnw 포함) / npm (frontend) |

## 폴더 구조

```
.
├── backend/    # Spring Boot API 서버 (포트 8080)
├── frontend/   # Next.js 웹 (포트 3000)
├── database/   # DB 초기화/마이그레이션 SQL 스크립트
└── docs/       # 기획서, 알고리즘 설계 문서 등 (OpenAI 안내서 기반 자료 포함)
```

## 사전 준비

- Java 21+
- Node.js 20.9+
- MySQL 8.0 (로컬에 설치되어 실행 중이어야 함)

## 1. 데이터베이스 초기화

MySQL 서버가 로컬에서 실행 중인 상태에서:

```bash
mysql -u root -p < database/00_init_database.sql
```

- 계정: `root` / 비밀번호: `0000` (로컬 개발 전용, `backend/src/main/resources/application-local.yml`에 설정됨)
- 생성되는 스키마: `festival_budget`

## 2. 백엔드 실행 (Spring Boot)

```bash
cd backend
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

- 기본 프로필: `local` (`application.yml`의 `spring.profiles.active`)
- 실행 확인: http://localhost:8080/api/health , http://localhost:8080/actuator/health
- DB 접속 정보는 `application-local.yml`에서 관리하며 git에는 커밋되지 않는다 (`.example` 템플릿 참고).

## 3. 프론트엔드 실행 (Next.js)

```bash
cd frontend
npm install   # 최초 1회
npm run dev
```

- http://localhost:3000 접속 시 백엔드 헬스체크 API 연동 여부를 화면에서 바로 확인 가능
- API 베이스 URL은 `.env.local`의 `NEXT_PUBLIC_API_BASE_URL`로 관리 (`.env.local.example` 참고)

## 4. 원본 데이터 Import (엑셀 → DB)

원본 엑셀(2026년 지역축제 개최 계획 현황)에는 담당자 성명·연락처 등 개인정보 컬럼이 포함되어
있어 **저장소 안에 두지 않는다.** 파일은 로컬 아무 경로(예: 다운로드 폴더)에 두고, 경로만
환경변수 또는 실행 인자로 전달한다. 자세한 예시는 `backend/.env.example` 참고.

Import는 HTTP API가 아니라 **명시적 CLI 실행**으로만 동작하며, 일반 서버 실행(`spring-boot:run`
단독 실행)에는 절대 관여하지 않는다. `import.run=true`를 줬을 때만 동작하고, 이때는 내장
웹서버도 띄우지 않고 Import만 수행한 뒤 프로세스가 종료된다.

```powershell
cd backend
$env:FESTIVAL_EXCEL_PATH = "C:/Users/yourname/Downloads/2026년 지역축제 개최 계획 현황(공개용).xlsx"
./mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--import.run=true"
```

동작 방식 요약:

- 파일 바이트의 **SHA-256 해시**로 동일 파일 여부를 판단한다(파일명은 신뢰하지 않음). 같은 해시가
  이미 성공 처리되어 있으면 DB를 전혀 건드리지 않고 종료한다(no-op).
- 시트 존재 여부·헤더 8개 셀·전체 행 파싱·필수 코드(지역/축제유형/장소유형/개최주기) 인식을
  모두 통과해야 DB에 반영된다. 하나라도 실패하면 **DB는 전혀 변경되지 않는다.**
- 같은 `datasetYear`에 대해 재실행하면 검증 통과 후 하나의 트랜잭션 안에서 기존 연도 데이터를
  삭제하고 새 데이터로 교체한다(행별 upsert 아님). 중간에 실패하면 기존 데이터가 그대로 유지된다.
- 담당자 성명/연락처/비고 등 개인정보·자유서술 컬럼(AI~AN)은 애초에 읽지도 않는다.
- Import 결과(전체 행 수, 예산 상태별 건수, 지역/유형 종류 수, 경고 목록)는 콘솔에 출력되고
  `dataset_import_batch`/`import_warning` 테이블에도 감사 기록으로 남는다.

## 5. Import 결과 검증 화면 (/admin/datasets)

CLI로 Import한 결과를 눈으로 확인할 수 있는 **읽기 전용** 관리자 화면. 쓰기/업로드 기능은 없다.

- 프론트: http://localhost:3000/admin/datasets
- 백엔드 API: `GET /api/v1/admin/datasets/latest`(및 `/summary`, `/distributions`, `/issues`, `/sample`)
- `backend/src/main/resources/application-local.yml`의 `festival.admin-ui.enabled: true`일 때만
  컨트롤러 빈이 생성된다. 기본값(`application.yml`)은 `false`이므로 별도 설정 없이는 항상 404.
- 상단 지표 카드(전체 행 수, 예산 상태별 건수, 기간 누락, 지역/유형/장소유형 종류 수)는
  2026년 알려진 기준값과 자동 비교되어 불일치 시 화면에 표시된다.
- 담당자 정보 등 개인정보 컬럼은 애초에 DB에 없으므로 이 화면에도 나타날 수 없다
  (`개인정보성 컬럼 저장 결과: 저장되지 않음` 문구로 명시).

```bash
cd backend
./mvnw spring-boot:run   # 관리자 API 포함 전체 서버 기동 (import.run 없이)
```

## 6. 축제 예산 판단 어시스트 (/budget-assistant)

지역·축제유형·장소유형·개최기간을 입력하면 유사 축제 데이터 기반 참고 예산을 계산한다.

- 프론트: http://localhost:3000/budget-assistant
- 백엔드 API: `GET /api/v1/metadata`(선택지), `POST /api/v1/budget-estimates`(계산) — 둘 다 인증 없이 항상 켜져 있다(개인정보 없음).
- 알고리즘 가중치/임계값은 전부 `backend/.../estimate/AlgorithmConfig.java`에서 관리하며 `festival.algorithm.*`로 덮어쓸 수 있다. 버전은 `v1.0.0`.
- 표본이 부족하면 시군구→광역→전국 순으로 조건을 넓히는 계층형 fallback이 자동 적용되고, 응답에 `fallbackLevel`/`fallbackLabel`로 어디까지 넓혔는지 그대로 노출한다.
- `festival.calculation-trace.enabled: true`(로컬 기본값)일 때만 응답에 `calculationTrace`(계산 단계별 서술)가 포함되어 화면 하단에 "계산 상세" 패널로 보인다. 운영에서는 이 필드 자체가 응답에 없다.

```bash
cd backend
./mvnw spring-boot:run   # 관리자 API + 예산 추정 API 포함 전체 서버 기동
```

## 개발 방향

- 공공기관 실사용 목적이므로 디자인보다 데이터 정합성·예산 추천 로직의 정확성·검증 가능한 API를 우선한다.
- 축제 예산 추천 알고리즘 설계는 `docs/`에 정리된 자료(OpenAI 안내서 기반)를 기준으로 진행한다.