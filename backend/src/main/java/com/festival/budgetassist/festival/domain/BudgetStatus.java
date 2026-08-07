package com.festival.budgetassist.festival.domain;

/**
 * 예산(V열) 값의 상태.
 *
 * <p>원본 값은 다음 넷 중 하나다.</p>
 * <ul>
 *   <li>{@link #CONFIRMED} - 숫자이고 0보다 큼 (실제 1,238건, 예산 추정 알고리즘의 유효 표본)</li>
 *   <li>{@link #ZERO} - 숫자이고 정확히 0 (실제 1건)</li>
 *   <li>{@link #UNCONFIRMED} - 원본 값이 "미확정" (실제 22건)</li>
 *   <li>{@link #NO_RESPONSE} - 원본 값이 "무응답" (실제 5건)</li>
 * </ul>
 */
public enum BudgetStatus {
    CONFIRMED,
    ZERO,
    UNCONFIRMED,
    NO_RESPONSE
}