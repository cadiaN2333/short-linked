package com.lzq.shortlink.cache;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.lzq.shortlink.config.ShortLinkBloomProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 单实例短码布隆过滤器。
 *
 * 仅完整初始化后允许负判断。
 * 创建与初始化共用生命周期锁，查询不等待初始化锁。
 */
@Slf4j
@Component
public class ShortCodeBloomFilter {

    private enum State {
        DISABLED,
        BUILDING,
        READY,
        BYPASS
    }

    private record Snapshot(
            State state,
            BloomFilter<String> filter
    ) {
    }

    private final ShortLinkBloomProperties properties;

    private final Object lifecycleLock = new Object();

    private final AtomicReference<Snapshot> snapshot;

    private boolean initializationAttempted;

    private volatile boolean tainted;

    public ShortCodeBloomFilter(
            ShortLinkBloomProperties properties
    ) {
        this.properties = properties;
        this.snapshot = new AtomicReference<>(new Snapshot(
                properties.enabled()
                        ? State.BUILDING
                        : State.DISABLED,
                null
        ));
    }

    /** 保留给原有手动构造服务的测试使用。 */
    public static ShortCodeBloomFilter disabled() {
        return new ShortCodeBloomFilter(
                new ShortLinkBloomProperties(
                        false,
                        1000000,
                        0.001,
                        1000
                )
        );
    }

    public boolean isReady() {
        return !tainted && snapshot.get().state() == State.READY;
    }

    /**
     * 初始化只尝试一次。
     * 加载过程只操作候选对象，成功后一次性发布。
     */
    public void initialize(
            Consumer<Consumer<String>> loader
    ) {
        synchronized (lifecycleLock) {
            if (initializationAttempted) {
                return;
            }

            initializationAttempted = true;

            if (!properties.enabled() || tainted) {
                return;
            }

            Snapshot building = snapshot.get();
            if (building.state() != State.BUILDING) return;

            try {
                BloomFilter<String> candidate =
                        BloomFilter.create(
                                Funnels.stringFunnel(
                                        StandardCharsets.UTF_8
                                ),
                                properties.expectedInsertions(),
                                properties.falsePositiveProbability()
                        );

                loader.accept(code -> {
                    validateCode(code);
                    candidate.put(code);
                });

                // 扫描期间事务可能已永久降级，不能用 READY 覆盖 BYPASS。
                if (tainted || !snapshot.compareAndSet(building, new Snapshot(State.READY, candidate))) return;

                log.info(
                        "短码布隆过滤器初始化完成，估算元素数={}，估算误判率={}",
                        candidate.approximateElementCount(),
                        candidate.expectedFpp()
                );

                if (candidate.expectedFpp()
                        > properties.falsePositiveProbability() * 2) {
                    log.warn(
                            "短码布隆过滤器估算误判率偏高，请调整容量后重启重建"
                    );
                }
            } catch (RuntimeException exception) {
                bypassLocked(
                        "布隆过滤器初始化失败",
                        exception
                );
            }
        }
    }

    /**
     * 返回 false 才表示可以拦截。
     * 未初始化、已关闭或异常时始终返回 true。
     */
    public boolean mightContain(String shortCode) {
        if (tainted) return true;
        Snapshot current = snapshot.get();

        if (current.state() != State.READY) {
            return true;
        }

        try {
            return tainted || current.filter().mightContain(shortCode);
        } catch (RuntimeException exception) {
            // READY 后不会再次初始化；直接发布终止状态，避免与缓存锁反向等待。
            bypassLocked("布隆过滤器查询失败", exception);

            return true;
        }
    }

    /**
     * 包围完整创建流程。
     *
     * 当前创建使用数据库自动提交，所以方法返回前插入已完成。
     * 若调用方存在外层事务，则保守关闭负拦截，避免遗漏未提交记录。
     */
    public <T> T duringCreation(Supplier<T> action) {
        // 配置关闭后不会再初始化，直接执行，不能降低原有创建并发度。
        if (!properties.enabled() || tainted) {
            return action.get();
        }
        // 必须先于取锁：其他创建可能持锁等待本事务保留的数据库行锁。
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            bypassLocked("检测到外层创建事务，保守绕过布隆过滤器", null);
            return action.get();
        }
        synchronized (lifecycleLock) {
            if (!tainted) return action.get();
        }
        // 外层事务或组件降级后，不再持生命周期锁等待数据库提交。
        return action.get();
    }

    /** 每一次数据库插入尝试前登记候选短码。 */
    public void beforeInsert(String shortCode) {
        if (tainted || snapshot.get().state() != State.READY) return;
        synchronized (lifecycleLock) {
            Snapshot current = snapshot.get();

            if (current.state() != State.READY) {
                return;
            }

            try {
                validateCode(shortCode);

                // 返回 false 也不代表失败，可能只是相关位已经存在。
                current.filter().put(shortCode);
            } catch (RuntimeException exception) {
                bypassLocked(
                        "新增短码登记失败",
                        exception
                );
            }
        }
    }

    private void validateCode(String shortCode) {
        if (shortCode == null
                || !shortCode.matches("[0-9A-Za-z_-]{3,16}")) {
            throw new IllegalArgumentException(
                    "短码不符合当前路由支持的格式"
            );
        }
    }

    /** 单向发布终止状态；查询异常路径不能等待生命周期锁。 */
    private void bypassLocked(
            String reason,
            RuntimeException exception
    ) {
        tainted = true;
        snapshot.set(new Snapshot(State.BYPASS, null));

        if (exception == null) {
            log.warn("{}，继续使用原查询流程", reason);
        } else {
            log.warn(
                    "{}，继续使用原查询流程",
                    reason,
                    exception
            );
        }
    }
}
