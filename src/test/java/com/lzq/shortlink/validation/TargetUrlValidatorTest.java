package com.lzq.shortlink.validation;

import com.lzq.shortlink.exception.InvalidTargetUrlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** 验证地址结构边界及规范化不会破坏路径、查询和签名参数。 */
class TargetUrlValidatorTest {
    static Stream<String> invalidUrls() {
        return Stream.of(null, "", " ", "ftp://example.com", "javascript:alert(1)",
                "//example.com/a", "https:///a", "https:example.com", "https://",
                "https://user:pass@example.com/a", "https://user@example.com",
                "https://example.com/a b", "https://example.com/a\tb", "https://example.com/a\nb",
                "https://example.com/\\evil", "https://example.com/%00", "https://example.com/%0d%0A",
                "https://example.com/%1f", "https://example.com/%7F", "https://example.com/%zz",
                "https://example.com:0", "https://example.com:65536", "https://example.com:",
                "https://example.com:abc", "https://example.com:999999999999",
                "https://-bad.example", "https://bad_.example", "https://a..example",
                "https://%65xample.com", "http://[fe80::1%25eth0]/", "http://::1/",
                "https://example.com/\uD800", "https://example.com/\u00a0");
    }

    @ParameterizedTest
    @MethodSource("invalidUrls")
    void rejectsInvalidStructure(String url) {
        assertThrows(InvalidTargetUrlException.class, () -> TargetUrlValidator.parse(url));
    }

    static Stream<String> validUrls() {
        return Stream.of("https://example.com", "http://localhost:8080/", "http://127.0.0.1:1/",
                "https://example.com:65535/a", "http://[::1]:8080/a", "https://example.com/a%20b",
                "https://example.com/a/../b", "https://example.com/?#", "https://example.com/中文?q=中文");
    }

    @ParameterizedTest
    @MethodSource("validUrls")
    void acceptsValidStructureWithoutChangingSemantics(String url) {
        assertEquals(url, TargetUrlValidator.normalize(url));
    }

    @Test
    void normalizesOnlySchemeAndHost() {
        assertEquals("https://example.com/A%2fb?q=a+b&x=%26#X",
                TargetUrlValidator.normalize("HTTPS://EXAMPLE.COM/A%2fb?q=a+b&x=%26#X"));
    }

    @Test
    void convertsInternationalDomainToAscii() {
        assertEquals("https://xn--fsqu00a.xn--0zwm56d/中文",
                TargetUrlValidator.normalize("https://例子.测试/中文"));
    }

    @Test
    void enforcesExactLengthBoundary() {
        String prefix = "https://example.com/";
        String boundary = prefix + "a".repeat(2048 - prefix.length());
        assertEquals(boundary, TargetUrlValidator.normalize(boundary));
        assertThrows(InvalidTargetUrlException.class, () -> TargetUrlValidator.normalize(boundary + "a"));
    }
}
