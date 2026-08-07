package com.festival.budgetassist.admin;

import java.util.List;

/**
 * 선형 보간(linear interpolation) 방식의 분위수 계산. numpy의 기본(linear) 방식과 동일해서,
 * Phase 0에서 openpyxl/numpy로 미리 계산해둔 참고값과 바로 비교할 수 있다.
 */
final class Quantile {

    private Quantile() {
    }

    /** values는 이미 오름차순 정렬되어 있어야 한다. q는 0~1 사이. */
    static double linear(List<Long> sortedValues, double q) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        if (sortedValues.size() == 1) {
            return sortedValues.get(0);
        }
        double index = q * (sortedValues.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) {
            return sortedValues.get(lower);
        }
        double fraction = index - lower;
        return sortedValues.get(lower) + (sortedValues.get(upper) - sortedValues.get(lower)) * fraction;
    }
}