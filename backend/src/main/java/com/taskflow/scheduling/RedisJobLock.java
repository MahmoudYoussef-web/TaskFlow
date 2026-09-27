package com.taskflow.scheduling;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Distributed lock so N worker instances never execute the same job twice.
 * SET NX EX for acquire; Lua compare-and-del for safe release. Falls back to
 * allowing execution when Redis is unreachable would be WRONG — instead we
 * fail closed: no lock, no execution.
 */
@Component
public class RedisJobLock {
    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);

    public RedisJobLock(StringRedisTemplate redis,
                        @Value("${taskflow.worker.lock-ttl-seconds:120}") long ttlSeconds) {
        this.redis = redis;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public String instanceId() {
        return instanceId;
    }

    public boolean tryAcquire(UUID jobId) {
        Boolean ok = redis.opsForValue()
                .setIfAbsent(lockKey(jobId), instanceId, ttl);
        return Boolean.TRUE.equals(ok);
    }

    public void release(UUID jobId) {
        // Release only if we still own it.
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "return redis.call('del', KEYS[1]) else return 0 end";
        redis.execute(
                (org.springframework.data.redis.core.RedisCallback<Long>) conn ->
                        conn.scriptingCommands().eval(
                                script.getBytes(),
                                org.springframework.data.redis.connection.ReturnType.INTEGER,
                                1,
                                lockKey(jobId).getBytes(),
                                instanceId.getBytes()),
                true);
    }

    private static String lockKey(UUID jobId) {
        return "taskflow:job:lock:" + jobId;
    }

    /** For tests: how many times dispatch was attempted cluster-wide. */
    public Long bumpAttemptCounter(UUID jobId) {
        return redis.opsForValue().increment("taskflow:job:attempts:" + jobId);
    }

    public List<String> readLog(String key) {
        return redis.opsForList().range(key, 0, -1);
    }
}
