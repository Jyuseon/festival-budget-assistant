/**
 * 원(KRW) 단위 숫자를 사람이 읽기 쉬운 한국어 금액 문자열로 변환한다.
 * 내부 계산/저장은 항상 원 단위 정수를 쓰고, 화면 렌더링에서만 이 함수를 거친다
 * (가이드 12.3 원칙). /admin/datasets 뿐 아니라 이후 /budget-assistant 화면에서도 재사용한다.
 *
 * 예: 76,500,000 -> "7,650만원", 204,500,000 -> "2억 450만원", 1,100,000,000 -> "11억원"
 */
export function formatKrwCompact(amount: number | null | undefined): string {
  if (amount === null || amount === undefined || Number.isNaN(amount)) {
    return "-";
  }

  const rounded = Math.round(amount);
  const sign = rounded < 0 ? "-" : "";
  const abs = Math.abs(rounded);

  if (abs === 0) {
    return "0원";
  }

  const eok = Math.floor(abs / 100_000_000);
  const remainderAfterEok = abs % 100_000_000;
  const man = Math.floor(remainderAfterEok / 10_000);

  const parts: string[] = [];
  if (eok > 0) {
    parts.push(`${eok.toLocaleString("ko-KR")}억`);
  }
  if (man > 0) {
    parts.push(`${man.toLocaleString("ko-KR")}만`);
  }

  if (parts.length === 0) {
    return `${sign}${abs.toLocaleString("ko-KR")}원`;
  }
  return `${sign}${parts.join(" ")}원`;
}

/** 정확한 원 단위 숫자 문자열 (예: "76,500,000원"). 상세 확인용. */
export function formatKrwExact(amount: number | null | undefined): string {
  if (amount === null || amount === undefined || Number.isNaN(amount)) {
    return "-";
  }
  return `${Math.round(amount).toLocaleString("ko-KR")}원`;
}

/**
 * 백만원 단위 숫자(다년도 CSV/DB 원본 단위)를 원 단위 압축 표기로 변환한다.
 * DB/API는 항상 백만원 단위를 그대로 유지하고, 화면 표시에서만 이 함수를 거친다.
 * 예: 204.5 -> "2억 450만원"
 */
export function formatMillionKrwCompact(
  million: number | null | undefined,
): string {
  if (million === null || million === undefined || Number.isNaN(million)) {
    return "-";
  }
  return formatKrwCompact(million * 1_000_000);
}

export function formatNumber(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return "-";
  }
  return value.toLocaleString("ko-KR");
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) {
    return "-";
  }
  try {
    return new Date(iso).toLocaleString("ko-KR", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return iso;
  }
}