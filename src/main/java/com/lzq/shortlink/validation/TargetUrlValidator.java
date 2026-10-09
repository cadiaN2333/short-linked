package com.lzq.shortlink.validation;

import com.lzq.shortlink.exception.InvalidTargetUrlException;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

/** 校验目标地址结构，不发起网络请求。 */
public final class TargetUrlValidator {

    private static final int MAX_URL_LENGTH = 2048;

    // 编码控制字符
    private static final Pattern ENCODED_CONTROL =
            Pattern.compile(
                    "%(?:0[0-9a-f]|1[0-9a-f]|7f)",
                    Pattern.CASE_INSENSITIVE
            );
    private TargetUrlValidator() {
    }

    public static String normalize(String value) {
        return parse(value).toString();
    }

    // 校验目标地址是否符合要求
    public static URI parse(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("目标地址不能为空");
        }

        if (value.length() > MAX_URL_LENGTH) {
            throw invalid("原始链接长度不能超过 2048 个字符");
        }

        if (value.codePoints().anyMatch(c ->
                Character.isISOControl(c)
                        || Character.isWhitespace(c)
                        || Character.isSpaceChar(c)
                        || c == '\\'
                        || (c >= 0xD800 && c <= 0xDFFF))
                || ENCODED_CONTROL.matcher(value).find()) {
            throw invalid("原始链接不能包含空白、反斜杠或控制字符");
        }

        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();

            if (scheme == null
                    || (!"http".equalsIgnoreCase(scheme)
                    && !"https".equalsIgnoreCase(scheme))
                    || uri.isOpaque()) {
                throw invalid("原始链接只支持 HTTP 或 HTTPS");
            }

            String authority = uri.getRawAuthority();
            if (authority == null || authority.isEmpty()) {
                throw invalid("原始链接必须包含有效主机名");
            }

            if (uri.getRawUserInfo() != null || authority.contains("@")) {
                throw invalid("原始链接不能包含用户名或密码");
            }

            StringBuilder result = new StringBuilder()
                    .append(scheme.toLowerCase(Locale.ROOT))
                    .append("://")
                    .append(normalizeAuthority(authority));

            if (uri.getRawPath() != null) {
                result.append(uri.getRawPath());
            }
            if (uri.getRawQuery() != null) {
                result.append('?').append(uri.getRawQuery());
            }
            if (uri.getRawFragment() != null) {
                result.append('#').append(uri.getRawFragment());
            }

            if (result.length() > MAX_URL_LENGTH) {
                throw invalid("规范化后的原始链接长度不能超过 2048 个字符");
            }

            URI target = new URI(result.toString()).parseServerAuthority();
            if (target.getHost() == null || target.getHost().isEmpty()) {
                throw invalid("原始链接主机名不合法");
            }

            return target;
        } catch (URISyntaxException exception) {
            throw invalid("原始链接格式不正确");
        }
    }

    private static String normalizeAuthority(String authority) {
        String host;
        String portSuffix = "";

        if (authority.startsWith("[")) {
            int closing = authority.indexOf(']');
            if (closing < 0) {
                throw invalid("IPv6 地址格式不正确");
            }

            host = authority.substring(0, closing + 1);
            portSuffix = authority.substring(closing + 1);

            if (host.contains("%")) {
                throw invalid("目标地址不支持带作用域的 IPv6 地址");
            }
        } else {
            int colon = authority.lastIndexOf(':');
            if (colon >= 0) {
                if (authority.indexOf(':') != colon) {
                    throw invalid("IPv6 地址必须使用方括号");
                }
                host = authority.substring(0, colon);
                portSuffix = authority.substring(colon);
            } else {
                host = authority;
            }

            if (host.isEmpty() || host.contains("%")) {
                throw invalid("原始链接主机名不合法");
            }

            try {
                host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES)
                        .toLowerCase(Locale.ROOT);
            } catch (IllegalArgumentException exception) {
                throw invalid("原始链接主机名不合法");
            }

            if (host.isEmpty() || ".".equals(host) || host.length() > 253) {
                throw invalid("原始链接主机名不合法");
            }
        }

        validatePort(portSuffix);
        return host + portSuffix;
    }

    private static void validatePort(String suffix) {
        if (suffix.isEmpty()) {
            return;
        }

        if (!suffix.matches(":[0-9]+")) {
            throw invalid("原始链接端口格式不正确");
        }

        try {
            int port = Integer.parseInt(suffix.substring(1));
            if (port < 1 || port > 65535) {
                throw invalid("原始链接端口必须在 1 到 65535 之间");
            }
        } catch (NumberFormatException exception) {
            throw invalid("原始链接端口格式不正确");
        }
    }

    private static InvalidTargetUrlException invalid(String message) {
        return new InvalidTargetUrlException(message);
    }
}
