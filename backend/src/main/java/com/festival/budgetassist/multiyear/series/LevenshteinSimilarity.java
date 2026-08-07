package com.festival.budgetassist.multiyear.series;

/**
 * 의존성 없는 최소 Levenshtein 거리 기반 문자열 유사도. {@code ratio = 1 - editDistance/maxLen},
 * 범위는 [0,1] (완전 동일=1, 공통점 없음에 가까울수록 0에 가까움).
 */
final class LevenshteinSimilarity {

    private LevenshteinSimilarity() {
    }

    static double ratio(String a, String b) {
        if (a == null || b == null) {
            return 0.0;
        }
        if (a.equals(b)) {
            return 1.0;
        }
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) {
            return 1.0;
        }
        int distance = editDistance(a, b);
        return 1.0 - (double) distance / maxLen;
    }

    /** 길이 차이만으로 유사도 상한을 빠르게 걸러낼 수 있는지 확인 - 전체 DP를 돌리기 전 값싼 사전 필터용. */
    static double lengthBoundedMaxRatio(String a, String b) {
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) {
            return 1.0;
        }
        int lenDiff = Math.abs(a.length() - b.length());
        return 1.0 - (double) lenDiff / maxLen;
    }

    private static int editDistance(String a, String b) {
        int n = a.length();
        int m = b.length();
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[m];
    }
}