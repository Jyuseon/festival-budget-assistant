package com.festival.budgetassist.multiyear.series;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 축제명에서 연도/회차 표기만 걷어내 "같은 축제의 다른 연도 표기"를 하나의 문자열로 모으기 위한
 * 정규화기.
 *
 * <p>목표 예시: {@code "제27회 무주반딧불축제"}, {@code "제28회 무주반딧불축제"},
 * {@code "2025 무주반딧불축제"} → {@code "무주반딧불축제"}.</p>
 *
 * <p>의도적으로 보수적이다:</p>
 * <ul>
 *   <li>제거하는 것: 앞쪽 연도(4자리), "제n회"/"n회", 괄호 안이 연도·회차·순수 숫자뿐인 경우의
 *       괄호 전체, 장식용 특수기호(「」『』【】〈〉), 중복/leading·trailing 공백.</li>
 *   <li>제거하지 않는 것: "축제"/"페스티벌" 등 표기 차이(공격적으로 통합하지 말라는 지시),
 *       회차/연도 문맥이 아닌 숫자(예: "3.1운동 100주년 기념축제"), 이름 내부의 단일 공백
 *       (공백 유무 차이는 여기서 지우지 않고 fuzzy 매칭 단계의 신호로 남겨둔다).</li>
 * </ul>
 *
 * <p>이 클래스는 순수 문자열 함수라 DB/Spring 없이 독립적으로 테스트 가능하다.</p>
 */
public final class FestivalNameNormalizer {

    // "2025", "2025년" 처럼 이름 맨 앞에 오는 연도.
    private static final Pattern LEADING_YEAR = Pattern.compile("^(19|20)\\d{2}\\s*년?\\s*");

    // "제27회", "제 27 회" 등 - 이름 어디에 있든 제거.
    private static final Pattern JE_ROUND = Pattern.compile("제\\s*\\d+\\s*회\\s*");

    // "27회", "10 회" - "제"가 없는 회차 표기.
    private static final Pattern BARE_ROUND = Pattern.compile("\\d+\\s*회\\s*");

    // 괄호 그룹 전체(내용 검사는 코드에서 별도로 함).
    private static final Pattern PAREN_GROUP = Pattern.compile("[(（][^)）]*[)）]");

    // 괄호 안 내용이 연도/회차/순수 숫자뿐인지 판정.
    private static final Pattern PAREN_CONTENT_IS_YEAR_OR_ROUND = Pattern.compile(
            "^((19|20)\\d{2}\\s*년?|제?\\s*\\d+\\s*회|\\d+)$");

    // 장식용 괄호/인용부호류 - 핵심 단어를 지우지 않는 순수 장식 문자만 대상으로 한다.
    private static final Pattern DECORATIVE_BRACKETS = Pattern.compile("[「」『』【】〈〉]");

    // 가장자리에 남은 연결부호(하이픈/가운뎃점/쉼표 등)를 정리하기 위한 패턴.
    private static final Pattern EDGE_CONNECTORS = Pattern.compile("^[\\s\\-·ㆍ,./]+|[\\s\\-·ㆍ,./]+$");

    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    private FestivalNameNormalizer() {
    }

    public static String normalize(String rawFestivalName) {
        if (rawFestivalName == null) {
            return null;
        }
        String s = rawFestivalName.trim();
        if (s.isEmpty()) {
            return s;
        }

        s = LEADING_YEAR.matcher(s).replaceFirst("");
        s = removeYearOrRoundParens(s);
        s = JE_ROUND.matcher(s).replaceAll(" ");
        s = BARE_ROUND.matcher(s).replaceAll(" ");
        s = DECORATIVE_BRACKETS.matcher(s).replaceAll("");
        s = WHITESPACE_RUN.matcher(s).replaceAll(" ");
        s = EDGE_CONNECTORS.matcher(s).replaceAll("");
        s = s.trim();

        return s.isEmpty() ? rawFestivalName.trim() : s;
    }

    /** 괄호 안 내용이 연도/회차/숫자뿐인 그룹만 통째로 제거하고, 그 외 괄호는 그대로 둔다. */
    private static String removeYearOrRoundParens(String s) {
        Matcher matcher = PAREN_GROUP.matcher(s);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            String content = s.substring(matcher.start() + 1, matcher.end() - 1).trim();
            result.append(s, lastEnd, matcher.start());
            if (!PAREN_CONTENT_IS_YEAR_OR_ROUND.matcher(content).matches()) {
                result.append(s, matcher.start(), matcher.end());
            } else {
                result.append(' ');
            }
            lastEnd = matcher.end();
        }
        result.append(s.substring(lastEnd));
        return result.toString();
    }

    /**
     * 문자열 유사도 비교(fuzzy 매칭)용 보조 키 - 정규화된 이름에서 공백까지 마저 제거한다.
     * 시트마다 공백 표기가 들쭉날쭉한 경우("무주 반딧불축제" vs "무주반딧불축제")를 fuzzy 단계에서
     * 잡기 위한 것이며, {@link #normalize}가 만드는 대표명(canonicalName)에는 쓰지 않는다 -
     * EXACT/NORMALIZED_EXACT 결정적 매칭 기준은 공백을 보존한 정규화 이름을 그대로 쓴다.
     */
    public static String fuzzyKey(String normalizedName) {
        return normalizedName == null ? null : WHITESPACE_RUN.matcher(normalizedName).replaceAll("");
    }
}