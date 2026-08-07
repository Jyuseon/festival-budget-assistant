package com.festival.budgetassist.admin.multiyear;

import java.util.List;

/**
 * 선형 보간 방식의 분위수 계산. {@link com.festival.budgetassist.admin.Quantile}과 같은 방식이지만
 * 그건 {@code List<Long>}(원 단위) 전용 package-private 유틸이라, 다년도 화면이 다루는
 * 백만원 단위 {@code double} 값에 맞춰 별도로 둔다.
 */
final class MultiYearQuantile {

    private MultiYearQuantile() {
    }

    /** values는 이미 오름차순 정렬되어 있어야 한다. q는 0~1 사이. */
    static double linear(List<Double> sortedValues, double q) {
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