package com.festival.budgetassist.multiyear.series;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DistrictPlaceholderNormalizerTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "-", "시자체", "시 자체", "본청", "도", "시", "지자체", "도자체", "미기재",
            "서울시", "울산시", "제주도", "세종시", "제주도 본청", "대구광역시",
            "인천관광공사", "대전마케팅공사", "서울관광재단", "인천도시공사",
            "서부공원녹지사업소", "대공원", "인천경제자유구역청", "울 산 시설공단",
            "경제청", "민간"
    })
    void recognizesConfirmedPlaceholders(String value) {
        assertTrue(DistrictPlaceholderNormalizer.isRegionLevelPlaceholder(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "시자체 (문화재단)", "도자체 (관광과)", "도자체 (식품 유통과)"
    })
    void recognizesPlaceholdersWithTrailingParenthetical(String value) {
        assertTrue(DistrictPlaceholderNormalizer.isRegionLevelPlaceholder(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "중구", "보령시", "무주군", "가평군", "종로구", "제주시", "서귀포시"
    })
    void realDistrictsAreNotPlaceholders(String value) {
        assertFalse(DistrictPlaceholderNormalizer.isRegionLevelPlaceholder(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // 실제 시군구 + 부가정보/접미어 - placeholder로 오판하면 안 됨
            "중구청", "제주시 건입동", "청주시 청원구 오창읍", "수원시 장안구",
            "용인시 처인구", "김포시 (양촌읍)", "서귀포시 표선면", "제주시 (삼양동)",
            // 오타로 보이는 값 - 이번 패스의 대상이 아님(placeholder 아님)
            "에산군", "김친시",
            // 실제 면 단위
            "지천면",
            // 여러 시군구/placeholder가 섞인 복합값 - 정보 손실 우려로 이번 패스에서 제외
            "대구광역시, 중구", "시자체,중구", "시자체,북구", "시자체 강화군",
            "종로구, 중구", "서초구, 영등포구", "동구,중구", "본청, 제천시", "본청, 영동군",
            "민간추진(대덕구)", "중구 (인천관광공사)"
    })
    void intentionallyExcludedValuesAreNotTreatedAsPlaceholders(String value) {
        assertFalse(DistrictPlaceholderNormalizer.isRegionLevelPlaceholder(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankIsTreatedAsPlaceholder(String value) {
        assertTrue(DistrictPlaceholderNormalizer.isRegionLevelPlaceholder(value));
    }

    @org.junit.jupiter.api.Test
    void nullIsTreatedAsPlaceholder() {
        assertTrue(DistrictPlaceholderNormalizer.isRegionLevelPlaceholder(null));
    }
}