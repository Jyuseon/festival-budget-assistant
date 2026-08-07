package com.festival.budgetassist.admin;

import java.util.List;

/**
 * '조사표' 시트 열 중 실제로 적재/제외되는 목록을 사람이 읽을 수 있는 문구로 고정해둔 것.
 * {@link com.festival.budgetassist.dataset.ExcelColumns}에 상수가 있으면 적재 대상,
 * 없으면 제외 대상이라는 사실과 항상 일치하도록 유지해야 한다(코드 리뷰 시 함께 확인).
 */
final class AdminColumnCatalog {

    static final List<String> LOADED_COLUMNS = List.of(
            "연번(B)", "광역자치단체명(C)", "기초자치단체명(D)", "축제명(E)", "축제 유형(F)",
            "개최 장소명(G)", "개최 장소 유형(H)", "개최지 시도(I)", "개최지 시군구(J)",
            "개최기간 시작/종료 년월일(L~Q)", "총 일수(R)", "개최기간 비고(S)",
            "개최 주기(T)", "최초 개최연도(U)",
            "예산 합계/국비/지방비/기타(V~Y, 원 단위로 변환)",
            "방문객수 전체/내국인/외국인(AA~AC, 2차 기능용 nullable)", "방문객수 계측방법(AD, 2차 기능용)"
    );

    static final List<String> EXCLUDED_COLUMNS = List.of(
            "개최지 읍면동(K) - MVP 미사용",
            "국비지원 부처명(Z) - 가이드 DB 모델에 없음",
            "방문객 비고(AE) - 자유서술",
            "축제 주최 전담조직(AF~AH) - 가이드 DB 모델에 없음",
            "담당자 소속(AI)", "담당자 부서(AJ)", "담당자 직급·직책(AK)",
            "담당자 성명(AL)", "연락처(AM)", "비고(AN) - 자유서술, 개인정보 위험"
    );

    static final String PERSONAL_INFO_STATUS_LABEL = "개인정보성 컬럼 저장 결과: 저장되지 않음";

    private AdminColumnCatalog() {
    }
}