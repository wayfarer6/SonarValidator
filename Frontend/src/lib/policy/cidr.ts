/**
 * IPv4 CIDR 입력의 형식 검사입니다.
 *
 * <h2>왜 클라이언트에서도 검사하는가</h2>
 * <p>최종 판정은 서버가 합니다({@code ProjectService.requireValidSubnetCidrs}).
 * 하지만 {@code 10.10.131.999/24} 같은 값은 저장을 누르는 순간에야 알 수 있어
 * 운영자가 어느 행을 고쳐야 하는지 파악하기 어렵습니다. 그래서 같은 규칙을
 * 화면에서도 적용해 <b>행 단위 오류</b>를 즉시 보여줍니다.
 *
 * <p>서버 규칙({@code PolicySubnet.isValidCidr})과 동일하게 맞춥니다.
 * 한쪽만 바뀌면 "화면은 통과인데 서버가 400" 또는 그 반대가 되므로,
 * 두 규칙을 함께 고쳐야 합니다.
 */

/**
 * CIDR 문자열의 형식을 검사합니다.
 *
 * <p>빈 값은 오류가 아닙니다. 자동 수집 전 서브넷은 대역이 비어 있을 수 있고,
 * 그 상태는 검증 엔진이 {@code classification-required} 로 안내합니다.
 *
 * @param value 검사할 CIDR (null 허용)
 * @returns 오류 문구, 유효하면 null
 */
export function validateIpv4Cidr(value: string | null | undefined): string | null {
  const text = (value ?? "").trim();
  if (!text) return null;

  const parts = text.split("/");
  if (parts.length !== 2) {
    return "CIDR 은 a.b.c.d/길이 형식이어야 합니다.";
  }

  const [ip, bits] = parts;
  const octets = ip.split(".");
  if (octets.length !== 4) {
    return "IPv4 주소는 옥텟 4개여야 합니다.";
  }
  for (const octet of octets) {
    if (!/^\d{1,3}$/.test(octet) || Number(octet) > 255) {
      return "각 옥텟은 0~255 사이 숫자여야 합니다.";
    }
  }

  if (!/^\d+$/.test(bits) || Number(bits) > 32) {
    return "접두사 길이는 0~32 사이여야 합니다.";
  }

  return null;
}

/**
 * 서브넷 목록에서 잘못된 CIDR 을 가진 행을 찾습니다.
 *
 * @param subnets 검사할 서브넷 ({@code id}, {@code cidr} 보유)
 * @returns {@code subnetId → 오류 문구} 맵 (오류가 없으면 빈 객체)
 */
export function collectCidrErrors(
  subnets: { id: string; cidr: string }[],
): Record<string, string> {
  const errors: Record<string, string> = {};
  for (const subnet of subnets) {
    const message = validateIpv4Cidr(subnet.cidr);
    if (message) errors[subnet.id] = message;
  }
  return errors;
}
