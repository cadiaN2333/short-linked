package com.lzq.shortlink.cache;

import com.lzq.shortlink.config.ShortLinkBloomProperties;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ShortCodeBloomFilterTest {

    @Test
    void taintedFlagMustPreventNegativeDecisionBeforeSnapshotPublication() {
        ShortCodeBloomFilter filter = enabledFilter();
        filter.initialize(add -> {});
        // 固定在降级标志已写入、快照尚未发布的窄窗口。
        org.springframework.test.util.ReflectionTestUtils.setField(filter, "tainted", true);
        assertTrue(filter.mightContain("new123"));
        assertFalse(filter.isReady());
    }

    @Test
    void transactionalCreationMustBypassBeforeWaitingForLifecycleLock() throws Exception {
        ShortCodeBloomFilter filter = enabledFilter();
        filter.initialize(add -> {});
        Object lifecycle = org.springframework.test.util.ReflectionTestUtils.getField(filter, "lifecycleLock");
        assertNotNull(lifecycle);
        var executor = Executors.newSingleThreadExecutor();
        try {
            synchronized (lifecycle) {
                var creation = executor.submit(() -> {
                    TransactionSynchronizationManager.setActualTransactionActive(true);
                    try {
                        return filter.duringCreation(() -> { filter.beforeInsert("new123"); return true; });
                    } finally {
                        TransactionSynchronizationManager.setActualTransactionActive(false);
                    }
                });
                assertTrue(creation.get(1, TimeUnit.SECONDS));
            }
            assertFalse(filter.isReady());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void transactionDuringInitializationCannotBeOverwrittenByReadyPublication() throws Exception {
        ShortCodeBloomFilter filter = enabledFilter();
        CountDownLatch scanning = new CountDownLatch(1);
        CountDownLatch finishScan = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var initialization = executor.submit(() -> filter.initialize(add -> {
                scanning.countDown();
                try {
                    if (!finishScan.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("扫描等待超时");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }));
            assertTrue(scanning.await(5, TimeUnit.SECONDS));
            var creation = executor.submit(() -> {
                TransactionSynchronizationManager.setActualTransactionActive(true);
                try {
                    filter.duringCreation(() -> { filter.beforeInsert("new123"); return null; });
                } finally {
                    TransactionSynchronizationManager.setActualTransactionActive(false);
                }
            });
            creation.get(1, TimeUnit.SECONDS);
            finishScan.countDown();
            initialization.get(5, TimeUnit.SECONDS);
            assertFalse(filter.isReady());
            assertTrue(filter.mightContain("new123"));
        } finally {
            finishScan.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void queryFailureMustBypassWithoutWaitingForLifecycleLock() throws Exception {
        ShortCodeBloomFilter filter = enabledFilter();
        filter.initialize(add -> {});
        Object lifecycle = org.springframework.test.util.ReflectionTestUtils.getField(filter, "lifecycleLock");
        assertNotNull(lifecycle);
        var executor = Executors.newSingleThreadExecutor();
        try {
            synchronized (lifecycle) {
                // 空参数触发真实 Guava 查询异常，降级不得等待创建线程持有的锁。
                var result = executor.submit(() -> filter.mightContain(null));
                assertTrue(result.get(1, TimeUnit.SECONDS));
            }
            assertFalse(filter.isReady());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void disabledFilteringMustNotSerializeCreations() throws Exception {
        ShortCodeBloomFilter filter = ShortCodeBloomFilter.disabled();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> filter.duringCreation(() -> {
                firstEntered.countDown();
                try {
                    if (!releaseFirst.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("测试等待超时");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return 1;
            }));
            assertTrue(firstEntered.await(5, TimeUnit.SECONDS));
            var second = executor.submit(() -> filter.duringCreation(() -> 2));
            assertEquals(2, second.get(1, TimeUnit.SECONDS));
            releaseFirst.countDown();
            assertEquals(1, first.get(5, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    private ShortCodeBloomFilter enabledFilter() {
        return new ShortCodeBloomFilter(
                new ShortLinkBloomProperties(
                        true,
                        10000,
                        0.001,
                        1000
                )
        );
    }

    @Test
    void initializationNotCompletedMustAllowQueries() {
        ShortCodeBloomFilter filter = enabledFilter();

        assertTrue(filter.mightContain("abc123"));
    }

    @Test
    void loadedCodesMustNotBeRejected() {
        ShortCodeBloomFilter filter = enabledFilter();

        filter.initialize(add -> {
            add.accept("abc123");
            add.accept("ABC123");
        });

        assertTrue(filter.isReady());
        assertTrue(filter.mightContain("abc123"));
        assertTrue(filter.mightContain("ABC123"));
    }

    @Test
    void emptyDatabaseCanPublishAnEmptyFilter() {
        ShortCodeBloomFilter filter = enabledFilter();

        filter.initialize(add -> {
        });

        assertTrue(filter.isReady());
        assertFalse(filter.mightContain("missing123"));
    }

    @Test
    void failedInitializationMustBypassFiltering() {
        ShortCodeBloomFilter filter = enabledFilter();

        filter.initialize(add -> {
            add.accept("abc123");
            throw new IllegalStateException("模拟加载失败");
        });

        assertFalse(filter.isReady());
        assertTrue(filter.mightContain("missing123"));
    }

    @Test
    void candidateMustBeRegisteredBeforeDatabaseInsert() {
        ShortCodeBloomFilter filter = enabledFilter();
        filter.initialize(add -> {
        });

        filter.duringCreation(() -> {
            filter.beforeInsert("new123");

            // 模拟即将执行数据库插入。
            assertTrue(filter.mightContain("new123"));
            return null;
        });

        assertTrue(filter.mightContain("new123"));
    }

    @Test
    void failedInsertDoesNotRemoveRegisteredBits() {
        ShortCodeBloomFilter filter = enabledFilter();
        filter.initialize(add -> {
        });

        assertThrows(IllegalStateException.class, () ->
                filter.duringCreation(() -> {
                    filter.beforeInsert("new123");
                    throw new IllegalStateException("模拟插入失败");
                })
        );

        assertTrue(filter.mightContain("new123"));
    }

    @Test
    void outerTransactionMustDisableNegativeFiltering() {
        ShortCodeBloomFilter filter = enabledFilter();
        filter.initialize(add -> {
        });

        TransactionSynchronizationManager
                .setActualTransactionActive(true);

        try {
            filter.duringCreation(() -> {
                filter.beforeInsert("new123");
                return null;
            });
        } finally {
            TransactionSynchronizationManager
                    .setActualTransactionActive(false);
        }

        assertFalse(filter.isReady());
        assertTrue(filter.mightContain("missing123"));
    }

    @Test
    void initializationCannotPassAnUnfinishedCreation()
            throws Exception {

        ShortCodeBloomFilter filter = enabledFilter();
        CountDownLatch creationEntered = new CountDownLatch(1);
        CountDownLatch allowCreationFinish = new CountDownLatch(1);
        CountDownLatch initializerStarted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var creation = executor.submit(() ->
                    filter.duringCreation(() -> {
                        creationEntered.countDown();

                        try {
                            if (!allowCreationFinish.await(
                                    5,
                                    TimeUnit.SECONDS
                            )) {
                                throw new IllegalStateException(
                                        "等待创建完成超时"
                                );
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }

                        return null;
                    })
            );

            assertTrue(creationEntered.await(
                    5,
                    TimeUnit.SECONDS
            ));

            var initialization = executor.submit(() -> {
                initializerStarted.countDown();

                // 模拟创建完成后数据库能够读取到的新短码。
                filter.initialize(add -> add.accept("new123"));
            });

            assertTrue(initializerStarted.await(
                    5,
                    TimeUnit.SECONDS
            ));

            assertFalse(filter.isReady());

            allowCreationFinish.countDown();

            creation.get(5, TimeUnit.SECONDS);
            initialization.get(5, TimeUnit.SECONDS);

            assertTrue(filter.isReady());
            assertTrue(filter.mightContain("new123"));
        } finally {
            allowCreationFinish.countDown();
            executor.shutdownNow();
        }
    }
}
