package com.festival.budgetassist.multiyear.csv;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 의존성 없는 최소 JSON 파서(테스트 전용). manifest/year_profiles.json처럼 프로젝트에 통제된
 * 신뢰할 수 있는 JSON을 읽기 위한 것으로, jackson 등 별도 라이브러리를 테스트 의존성에 추가하지
 * 않기 위해 이 정도 크기로 직접 구현했다.
 *
 * <p>객체는 {@code Map<String,Object>}, 배열은 {@code List<Object>}, 숫자는 {@code Double},
 * 문자열/불리언/null은 각각 그대로 매핑한다.</p>
 *
 * <p>{@code public}인 이유: {@code admin.multiyear} 패키지의 실데이터 acceptance 테스트도
 * 같은 manifest JSON을 읽어야 해서 재사용한다(패키지 간 테스트 유틸이라 jackson을 새로
 * 추가하지 않는 편이 낫다).</p>
 */
public final class SimpleJsonParser {

    private final String text;
    private int pos;

    private SimpleJsonParser(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        SimpleJsonParser parser = new SimpleJsonParser(text);
        parser.skipWhitespace();
        Object value = parser.parseValue();
        parser.skipWhitespace();
        return value;
    }

    private Object parseValue() {
        char c = text.charAt(pos);
        return switch (c) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> parseString();
            case 't', 'f' -> parseBoolean();
            case 'n' -> parseNull();
            default -> parseNumber();
        };
    }

    private Map<String, Object> parseObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        expect('{');
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            Object value = parseValue();
            map.put(key, value);
            skipWhitespace();
            char next = text.charAt(pos);
            if (next == ',') {
                pos++;
                continue;
            }
            if (next == '}') {
                pos++;
                break;
            }
            throw new IllegalStateException("JSON 파싱 오류(object) at " + pos);
        }
        return map;
    }

    private List<Object> parseArray() {
        List<Object> list = new ArrayList<>();
        expect('[');
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(parseValue());
            skipWhitespace();
            char next = text.charAt(pos);
            if (next == ',') {
                pos++;
                continue;
            }
            if (next == ']') {
                pos++;
                break;
            }
            throw new IllegalStateException("JSON 파싱 오류(array) at " + pos);
        }
        return list;
    }

    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = text.charAt(pos++);
            if (c == '"') {
                break;
            }
            if (c == '\\') {
                char escaped = text.charAt(pos++);
                switch (escaped) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        String hex = text.substring(pos, pos + 4);
                        sb.append((char) Integer.parseInt(hex, 16));
                        pos += 4;
                    }
                    default -> throw new IllegalStateException("알 수 없는 이스케이프: \\" + escaped);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Boolean parseBoolean() {
        if (text.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (text.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        throw new IllegalStateException("JSON 파싱 오류(boolean) at " + pos);
    }

    private Object parseNull() {
        if (text.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw new IllegalStateException("JSON 파싱 오류(null) at " + pos);
    }

    private Double parseNumber() {
        int start = pos;
        while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        return Double.parseDouble(text.substring(start, pos));
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        return text.charAt(pos);
    }

    private void expect(char c) {
        if (text.charAt(pos) != c) {
            throw new IllegalStateException("JSON 파싱 오류: '%s' 예상, 실제 '%s' at %d".formatted(c, text.charAt(pos), pos));
        }
        pos++;
    }
}