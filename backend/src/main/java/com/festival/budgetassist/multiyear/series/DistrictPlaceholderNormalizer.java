package com.festival.budgetassist.multiyear.series;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * district(시군구) 값 중 실제 기초자치단체를 가리키지 않는 "region-level placeholder"를
 * 식별한다. {@link FestivalSeriesLinkingService}가 series 그룹핑 키를 계산할 때만 사용하고,
 * {@code MultiYearFestivalRecord.districtRaw/districtText}는 절대 건드리지 않는다 - 원본 값은
 * DB에 그대로 남는다.
 *
 * <p>목록은 추측이 아니라 실제 적재된 10,198행의 {@code district_raw}/{@code district_text}
 * 전체 빈도표(2026-08-08 기준, 258종 distinct)를 검토해서 만들었다. "확실한" 것만 넣었고,
 * 애매한 값은 일부러 뺐다 - 아래 "의도적으로 제외한 것" 참고.</p>
 *
 * <h2>포함한 것(placeholder로 간주 - null 처리)</h2>
 * <ul>
 *   <li>범용 "구체적 시군구 아님" 표현: {@code -}(421건), {@code 시자체}(207), {@code 시 자체}(2),
 *       {@code 본청}(60), {@code 도}(19), {@code 시}(3), {@code 지자체}(3), {@code 도자체}(3),
 *       {@code 미기재}(1)</li>
 *   <li>광역지역명을 그대로 반복(시군구 정보가 전혀 추가되지 않음): {@code 서울시}(7),
 *       {@code 울산시}(9), {@code 제주도}(10), {@code 세종시}(4), {@code 제주도 본청}(3),
 *       {@code 대구광역시}(1, 단독 표기만 - "대구광역시, 중구"처럼 실제 구가 붙은 복합값은 제외)</li>
 *   <li>기초자치단체가 아닌 조직/시설명: {@code 인천관광공사}(7), {@code 대전마케팅공사}(3),
 *       {@code 서울관광재단}(1), {@code 인천도시공사}(1), {@code 서부공원녹지사업소}(3),
 *       {@code 대공원}(1), {@code 인천경제자유구역청}(3), {@code 울 산 시설공단}(2),
 *       {@code 경제청}(5), {@code 민간}(6)</li>
 *   <li>위 값에 괄호 부연설명이 붙은 변형(예: "시자체 (문화재단)") - 끝의 괄호를 떼고
 *       재판정한다.</li>
 * </ul>
 *
 * <h2>의도적으로 제외한 것(placeholder로 넣지 않음 - 원문 그대로 district 취급)</h2>
 * <ul>
 *   <li>실제 시군구 + 부가정보: "제주시 건입동", "청주시 청원구 오창읍", "수원시 장안구",
 *       "용인시 처인구"(2), "김포시 (양촌읍)", "서귀포시 표선면", "제주시 (삼양동)" - 진짜
 *       시군구 정보를 담고 있어 null로 만들면 오히려 정보 손실.</li>
 *   <li>실제 시군구 + 접미어: "중구청"(2) - "청"만 떼면 "중구"가 되지만, 이번 패스는 placeholder
 *       제거만 다루고 접미어 정제는 범위 밖이라 손대지 않았다.</li>
 *   <li>오타로 보이는 값: "에산군"(예산군 오타), "김친시"(김천시 오타) - 별도 오타 정정
 *       작업 대상이라 이번 정규화에 섞지 않았다.</li>
 *   <li>실제 면 단위: "지천면" - 시군구보다 더 세부적인 진짜 행정구역.</li>
 *   <li>여러 시군구/placeholder가 콤마 등으로 뒤섞인 복합값: "대구광역시, 중구",
 *       "시자체,중구", "시자체,북구", "시자체 강화군", "종로구, 중구", "서초구, 영등포구",
 *       "동구,중구", "본청, 제천시", "본청, 영동군", "민간추진(대덕구)", "중구 (인천관광공사)" -
 *       실제 시군구 정보가 섞여 있어 무조건 null 처리하면 정보를 잃는다. 어느 쪽이 대표값인지
 *       기계적으로 정하기 애매해 이번 패스에서는 원문 그대로 두고, 별도 정제 작업 후보로만
 *       남긴다.</li>
 * </ul>
 *
 * <p>참고: 세종("세종시")은 원본 자체에 시군구 하위 행정구역이 없는 특별자치시라, district
 * 값이 있어도 사실상 항상 region-level이다. 이번 목록에는 실제로 관측된 "세종시" 표기만
 * 반영했고, 세종이라는 이유만으로 다른 district 값까지 일괄 null 처리하지는 않는다(추후
 * 검토 후보).</p>
 */
final class DistrictPlaceholderNormalizer {

    private static final Set<String> PLACEHOLDERS = Set.of(
            "-", "시자체", "시 자체", "본청", "도", "시", "지자체", "도자체", "미기재",
            "서울시", "울산시", "제주도", "세종시", "제주도 본청", "대구광역시",
            "인천관광공사", "대전마케팅공사", "서울관광재단", "인천도시공사",
            "서부공원녹지사업소", "대공원", "인천경제자유구역청", "울 산 시설공단",
            "경제청", "민간"
    );

    private static final Pattern TRAILING_PAREN = Pattern.compile("\\s*[(（][^)）]*[)）]\\s*$");

    private DistrictPlaceholderNormalizer() {
    }

    /** {@code districtCandidate}는 이미 trim된 문자열이거나 null일 수 있다. */
    static boolean isRegionLevelPlaceholder(String districtCandidate) {
        if (districtCandidate == null || districtCandidate.isBlank()) {
            return true;
        }
        String trimmed = districtCandidate.trim();
        if (PLACEHOLDERS.contains(trimmed)) {
            return true;
        }
        String withoutTrailingParen = TRAILING_PAREN.matcher(trimmed).replaceAll("").trim();
        return !withoutTrailingParen.equals(trimmed) && PLACEHOLDERS.contains(withoutTrailingParen);
    }
}