import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/*
    ======================================================
                    RATE LIMITER - SDE2 DESIGN
    ======================================================

    FEATURES
    --------
    1. Strategy Pattern
    2. Factory Pattern
    3. Multiple User Tiers
    4. Thread Safe
    5. ConcurrentHashMap
    6. Fine Grained Locking
    7. Extensible
    8. Metrics
    9. Cleanup Ready
    10. Production Style Design

*/


/* ======================================================
                        USER TIER
   ====================================================== */

enum UserTier {
    FREE,
    PREMIUM,
    ENTERPRISE
}


/* ======================================================
                            USER
   ====================================================== */

class User {

    private final String userId;
    private final UserTier tier;

    public User(String userId, UserTier tier) {
        this.userId = userId;
        this.tier = tier;
    }

    public String getUserId() {
        return userId;
    }

    public UserTier getTier() {
        return tier;
    }
}


/* ======================================================
                        REQUEST
   ====================================================== */

class Request {

    private final User user;
    private final long timestamp;

    public Request(User user) {
        this.user = user;
        this.timestamp = System.currentTimeMillis();
    }

    public User getUser() {
        return user;
    }

    public long getTimestamp() {
        return timestamp;
    }
}


/* ======================================================
                    RATE LIMIT CONFIG
   ====================================================== */

class RateLimitConfig {

    private final int maxRequests;
    private final long windowSizeMillis;

    // Token Bucket Specific
    private final int capacity;
    private final int refillTokens;
    private final long refillIntervalMillis;

    private RateLimitConfig(Builder builder) {
        this.maxRequests = builder.maxRequests;
        this.windowSizeMillis = builder.windowSizeMillis;
        this.capacity = builder.capacity;
        this.refillTokens = builder.refillTokens;
        this.refillIntervalMillis = builder.refillIntervalMillis;
    }

    public int getMaxRequests() {
        return maxRequests;
    }

    public long getWindowSizeMillis() {
        return windowSizeMillis;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getRefillTokens() {
        return refillTokens;
    }

    public long getRefillIntervalMillis() {
        return refillIntervalMillis;
    }

    public static class Builder {

        private int maxRequests;
        private long windowSizeMillis;

        private int capacity;
        private int refillTokens;
        private long refillIntervalMillis;

        public Builder maxRequests(int maxRequests) {
            this.maxRequests = maxRequests;
            return this;
        }

        public Builder windowSizeMillis(long windowSizeMillis) {
            this.windowSizeMillis = windowSizeMillis;
            return this;
        }

        public Builder capacity(int capacity) {
            this.capacity = capacity;
            return this;
        }

        public Builder refillTokens(int refillTokens) {
            this.refillTokens = refillTokens;
            return this;
        }

        public Builder refillIntervalMillis(long refillIntervalMillis) {
            this.refillIntervalMillis = refillIntervalMillis;
            return this;
        }

        public RateLimitConfig build() {
            return new RateLimitConfig(this);
        }
    }
}


/* ======================================================
                CONFIG MANAGER
   ====================================================== */

class RateLimitConfigManager {

    private final Map<UserTier, RateLimitConfig> configMap =
            new ConcurrentHashMap<>();

    public void addConfig(
            UserTier tier,
            RateLimitConfig config
    ) {
        configMap.put(tier, config);
    }

    public RateLimitConfig getConfig(UserTier tier) {
        return configMap.get(tier);
    }
}


/* ======================================================
                    RESPONSE
   ====================================================== */

class RateLimitResponse {

    private final boolean allowed;
    private final long retryAfterMillis;

    public RateLimitResponse(
            boolean allowed,
            long retryAfterMillis
    ) {
        this.allowed = allowed;
        this.retryAfterMillis = retryAfterMillis;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public long getRetryAfterMillis() {
        return retryAfterMillis;
    }

    @Override
    public String toString() {
        return "Allowed = "
                + allowed
                + ", Retry After = "
                + retryAfterMillis + " ms";
    }
}


/* ======================================================
                    METRICS
   ====================================================== */

class Metrics {

    private final AtomicLong allowedRequests =
            new AtomicLong(0);

    private final AtomicLong rejectedRequests =
            new AtomicLong(0);

    public void incrementAllowed() {
        allowedRequests.incrementAndGet();
    }

    public void incrementRejected() {
        rejectedRequests.incrementAndGet();
    }

    public long getAllowedRequests() {
        return allowedRequests.get();
    }

    public long getRejectedRequests() {
        return rejectedRequests.get();
    }
}


/* ======================================================
                STRATEGY INTERFACE
   ====================================================== */

interface RateLimiterStrategy {

    RateLimitResponse allowRequest(Request request);
}


/* ======================================================
                    TOKEN BUCKET
   ====================================================== */

class TokenBucketRateLimiter
        implements RateLimiterStrategy {

    static class Bucket {

        int tokens;
        long lastRefillTimestamp;

        Bucket(int tokens, long lastRefillTimestamp) {
            this.tokens = tokens;
            this.lastRefillTimestamp = lastRefillTimestamp;
        }
    }

    private final RateLimitConfigManager configManager;

    /*
        THREAD SAFETY

        ConcurrentHashMap ensures:
        - thread safe reads/writes
        - no corruption
    */

    private final ConcurrentHashMap<String, Bucket> bucketMap =
            new ConcurrentHashMap<>();

    /*
        Fine grained locking.
        Each user gets independent lock.
    */

    private final ConcurrentHashMap<String, ReentrantLock>
            userLocks = new ConcurrentHashMap<>();

    private final Metrics metrics = new Metrics();

    public TokenBucketRateLimiter(
            RateLimitConfigManager configManager
    ) {
        this.configManager = configManager;
    }

    @Override
    public RateLimitResponse allowRequest(
            Request request
    ) {

        User user = request.getUser();

        String userId = user.getUserId();

        RateLimitConfig config =
                configManager.getConfig(
                        user.getTier()
                );

        /*
            THREAD SAFETY

            putIfAbsent is atomic.
        */

        userLocks.putIfAbsent(
                userId,
                new ReentrantLock()
        );

        ReentrantLock lock =
                userLocks.get(userId);

        /*
            Only same user's requests block each other.
            Different users can execute in parallel.
        */

        lock.lock();

        try {

            long currentTime =
                    System.currentTimeMillis();

            bucketMap.putIfAbsent(
                    userId,
                    new Bucket(
                            config.getCapacity(),
                            currentTime
                    )
            );

            Bucket bucket =
                    bucketMap.get(userId);

            refill(bucket, config, currentTime);

            if (bucket.tokens > 0) {

                bucket.tokens--;

                metrics.incrementAllowed();

                return new RateLimitResponse(
                        true,
                        0
                );
            }

            metrics.incrementRejected();

            return new RateLimitResponse(
                    false,
                    config.getRefillIntervalMillis()
            );

        } finally {

            /*
                VERY IMPORTANT

                Prevent deadlocks.
            */

            lock.unlock();
        }
    }

    private void refill(
            Bucket bucket,
            RateLimitConfig config,
            long currentTime
    ) {

        long elapsedTime =
                currentTime
                        - bucket.lastRefillTimestamp;

        long intervals =
                elapsedTime
                        / config.getRefillIntervalMillis();

        if (intervals > 0) {

            int tokensToAdd =
                    (int) intervals
                            * config.getRefillTokens();

            bucket.tokens = Math.min(
                    config.getCapacity(),
                    bucket.tokens + tokensToAdd
            );

            bucket.lastRefillTimestamp =
                    currentTime;
        }
    }

    public Metrics getMetrics() {
        return metrics;
    }
}


/* ======================================================
                    FIXED WINDOW
   ====================================================== */

class FixedWindowRateLimiter
        implements RateLimiterStrategy {

    static class WindowData {

        int count;
        long windowStart;

        WindowData(int count, long windowStart) {
            this.count = count;
            this.windowStart = windowStart;
        }
    }

    private final RateLimitConfigManager configManager;

    private final ConcurrentHashMap<String, WindowData>
            userWindowMap = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, ReentrantLock>
            userLocks = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(
            RateLimitConfigManager configManager
    ) {
        this.configManager = configManager;
    }

    @Override
    public RateLimitResponse allowRequest(
            Request request
    ) {

        User user = request.getUser();

        String userId = user.getUserId();

        RateLimitConfig config =
                configManager.getConfig(
                        user.getTier()
                );

        userLocks.putIfAbsent(
                userId,
                new ReentrantLock()
        );

        ReentrantLock lock =
                userLocks.get(userId);

        lock.lock();

        try {

            long currentTime =
                    System.currentTimeMillis();

            userWindowMap.putIfAbsent(
                    userId,
                    new WindowData(
                            0,
                            currentTime
                    )
            );

            WindowData data =
                    userWindowMap.get(userId);

            if (currentTime - data.windowStart
                    >= config.getWindowSizeMillis()) {

                data.count = 0;
                data.windowStart = currentTime;
            }

            if (data.count
                    < config.getMaxRequests()) {

                data.count++;

                return new RateLimitResponse(
                        true,
                        0
                );
            }

            return new RateLimitResponse(
                    false,
                    config.getWindowSizeMillis()
            );

        } finally {
            lock.unlock();
        }
    }
}


/* ======================================================
                    STRATEGY TYPE
   ====================================================== */

enum RateLimiterType {
    TOKEN_BUCKET,
    FIXED_WINDOW
}


/* ======================================================
                    FACTORY
   ====================================================== */

class RateLimiterFactory {

    public static RateLimiterStrategy create(
            RateLimiterType type,
            RateLimitConfigManager manager
    ) {

        switch (type) {

            case TOKEN_BUCKET:
                return new TokenBucketRateLimiter(
                        manager
                );

            case FIXED_WINDOW:
                return new FixedWindowRateLimiter(
                        manager
                );

            default:
                throw new IllegalArgumentException(
                        "Invalid type"
                );
        }
    }
}


/* ======================================================
                    SERVICE
   ====================================================== */

class RateLimiterService {

    private final RateLimiterStrategy strategy;

    public RateLimiterService(
            RateLimiterStrategy strategy
    ) {
        this.strategy = strategy;
    }

    public RateLimitResponse isAllowed(
            User user
    ) {

        Request request =
                new Request(user);

        return strategy.allowRequest(
                request
        );
    }
}


/* ======================================================
                    CLEANUP SERVICE
   ====================================================== */

class CleanupService {

    private final ScheduledExecutorService
            scheduler =
            Executors.newScheduledThreadPool(1);

    public void start() {

        scheduler.scheduleAtFixedRate(
                () -> System.out.println(
                        "Cleaning inactive users..."
                ),
                1,
                1,
                TimeUnit.MINUTES
        );
    }
}


/* ======================================================
                        CLIENT
   ====================================================== */

public class ratelimiter {

    public static void main(String[] args)
            throws InterruptedException {

        /*
            CONFIGURATION
        */

        RateLimitConfigManager manager =
                new RateLimitConfigManager();

        /*
            FREE USERS
        */

        manager.addConfig(
                UserTier.FREE,

                new RateLimitConfig.Builder()
                        .maxRequests(5)
                        .windowSizeMillis(10000)
                        .capacity(5)
                        .refillTokens(1)
                        .refillIntervalMillis(2000)
                        .build()
        );

        /*
            PREMIUM USERS
        */

        manager.addConfig(
                UserTier.PREMIUM,

                new RateLimitConfig.Builder()
                        .maxRequests(20)
                        .windowSizeMillis(10000)
                        .capacity(20)
                        .refillTokens(5)
                        .refillIntervalMillis(1000)
                        .build()
        );

        /*
            CREATE STRATEGY
        */

        RateLimiterStrategy strategy =
                RateLimiterFactory.create(
                        RateLimiterType.TOKEN_BUCKET,
                        manager
                );

        RateLimiterService service =
                new RateLimiterService(strategy);

        /*
            USERS
        */

        User freeUser =
                new User(
                        "free-user",
                        UserTier.FREE
                );

        User premiumUser =
                new User(
                        "premium-user",
                        UserTier.PREMIUM
                );

        /*
            THREAD POOL
        */

        ExecutorService executorService =
                Executors.newFixedThreadPool(10);

        Runnable freeTask = () -> {

            RateLimitResponse response =
                    service.isAllowed(freeUser);

            System.out.println(
                    Thread.currentThread().getName()
                            + " FREE -> "
                            + response
            );
        };

        Runnable premiumTask = () -> {

            RateLimitResponse response =
                    service.isAllowed(premiumUser);

            System.out.println(
                    Thread.currentThread().getName()
                            + " PREMIUM -> "
                            + response
            );
        };

        /*
            SIMULATE TRAFFIC
        */

        for (int i = 0; i < 15; i++) {

            executorService.submit(freeTask);

            executorService.submit(premiumTask);
        }

        executorService.shutdown();
    }
}