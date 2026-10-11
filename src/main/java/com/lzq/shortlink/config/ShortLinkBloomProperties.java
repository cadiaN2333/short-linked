package com.lzq.shortlink.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 单实例短码布隆过滤器配置。 */
@ConfigurationProperties(prefix = "app.short-link.bloom")
public record ShortLinkBloomProperties(
        @DefaultValue("false")
        boolean enabled,

        @DefaultValue("1000000")
        long expectedInsertions,

        @DefaultValue("0.001")
        double falsePositiveProbability,

        @DefaultValue("1000")
        int loadBatchSize
) {

        public ShortLinkBloomProperties {
                if (expectedInsertions <= 0
                        || expectedInsertions > 10_000_000) {
                        throw new IllegalArgumentException(
                                "布隆过滤器预期容量必须在 1 到 10000000 之间"
                        );
                }

                if (!Double.isFinite(falsePositiveProbability)
                        || falsePositiveProbability < 0.000001
                        || falsePositiveProbability > 0.1) {
                        throw new IllegalArgumentException(
                                "布隆过滤器误判率必须在 0.000001 到 0.1 之间"
                        );
                }

                if (loadBatchSize <= 0 || loadBatchSize > 10000) {
                        throw new IllegalArgumentException(
                                "布隆过滤器加载批大小必须在 1 到 10000 之间"
                        );
                }
        }
}