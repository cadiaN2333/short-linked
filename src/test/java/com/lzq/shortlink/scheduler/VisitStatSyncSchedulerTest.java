package com.lzq.shortlink.scheduler;

import com.lzq.shortlink.mapper.ShortLinkMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 访问统计定时同步测试。
 */
@ExtendWith(MockitoExtension.class)
class VisitStatSyncSchedulerTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ShortLinkMapper shortLinkMapper;

    @Mock
    private Cursor<String> cursor;

    private VisitStatSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new VisitStatSyncScheduler(
                stringRedisTemplate,
                shortLinkMapper
        );
    }

    @Test
    void shouldRequeueClaimedBatchWhenDatabaseWriteFails() {
        when(stringRedisTemplate.scan(any())).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn("short-link:visit:abc123");
        when(stringRedisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenReturn("5");
        when(shortLinkMapper.incrementVisitStatistics("abc123", 5L))
                .thenThrow(new DataAccessResourceFailureException("数据库暂时不可用"));

        assertDoesNotThrow(scheduler::syncVisitStatistics);

        verify(shortLinkMapper).incrementVisitStatistics("abc123", 5L);
        verify(stringRedisTemplate, times(2)).execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        );
        verify(stringRedisTemplate, never()).delete(anyString());
    }

    @Test
    void shouldAcknowledgeClaimedBatchAfterDatabaseWriteSucceeds() {
        when(stringRedisTemplate.scan(any())).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn("short-link:visit:abc123");
        when(stringRedisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenReturn("5");
        when(shortLinkMapper.incrementVisitStatistics("abc123", 5L))
                .thenReturn(1);

        scheduler.syncVisitStatistics();

        verify(shortLinkMapper).incrementVisitStatistics("abc123", 5L);
        verify(stringRedisTemplate).delete(anyString());
    }

    @Test
    void shouldNotRequeueAfterDatabaseSuccessWhenAcknowledgementDeleteFails() {
        when(stringRedisTemplate.scan(any())).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn("short-link:visit:abc123");
        when(stringRedisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenReturn("5");
        when(shortLinkMapper.incrementVisitStatistics("abc123", 5L))
                .thenReturn(1);
        doThrow(new RedisConnectionFailureException("Redis 暂时不可用"))
                .when(stringRedisTemplate)
                .delete(anyString());

        assertDoesNotThrow(scheduler::syncVisitStatistics);

        verify(shortLinkMapper).incrementVisitStatistics("abc123", 5L);
        verify(stringRedisTemplate).execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        );
    }

    @Test
    void shouldQuarantineBatchWhenShortLinkNoLongerExists() {
        when(stringRedisTemplate.scan(any())).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn("short-link:visit:abc123");
        when(stringRedisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenReturn("5");
        when(shortLinkMapper.incrementVisitStatistics("abc123", 5L))
                .thenReturn(0);

        scheduler.syncVisitStatistics();

        verify(stringRedisTemplate).rename(
                anyString(),
                org.mockito.ArgumentMatchers.startsWith("short-link:visit:orphan:")
        );
    }
}
