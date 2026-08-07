package com.festival.budgetassist.admin;

/**
 * GET /api/v1/admin/datasets/latest
 *
 * <p>{@code latestAttempt}는 성공/실패를 가리지 않은 가장 최근 시도, {@code latestSuccess}는
 * 지금 실제로 서비스되고 있는 데이터의 출처다. 최근 시도가 실패해도 이전 성공 데이터는
 * 그대로 남아있다는 Phase 2의 트랜잭션 보장을 화면에서 확인할 수 있게 두 값을 분리했다.</p>
 */
public record AdminDatasetOverviewResponse(
        boolean hasAnyAttempt,
        BatchInfo latestAttempt,
        boolean hasLiveData,
        BatchInfo latestSuccess
) {
    static AdminDatasetOverviewResponse empty() {
        return new AdminDatasetOverviewResponse(false, null, false, null);
    }
}