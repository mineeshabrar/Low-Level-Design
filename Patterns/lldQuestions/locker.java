import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;


// ======================================================
// ENUMS
// ======================================================

enum LockerSize {
    SMALL,
    MEDIUM,
    LARGE
}

enum LockerEventType {
    RESERVED,
    OCCUPIED,
    PICKED_UP,
    EXPIRED
}


// ======================================================
// PACKAGE
// ======================================================

class PackageItem {

    private String packageId;

    private LockerSize size;

    private String userId;

    public PackageItem(
            String packageId,
            LockerSize size,
            String userId) {

        this.packageId = packageId;
        this.size = size;
        this.userId = userId;
    }

    public String getPackageId() {
        return packageId;
    }

    public LockerSize getSize() {
        return size;
    }

    public String getUserId() {
        return userId;
    }
}


// ======================================================
// OBSERVER PATTERN
// ======================================================

class LockerEvent {

    private LockerEventType type;

    private String lockerId;

    private String packageId;

    public LockerEvent(
            LockerEventType type,
            String lockerId,
            String packageId) {

        this.type = type;
        this.lockerId = lockerId;
        this.packageId = packageId;
    }

    public LockerEventType getType() {
        return type;
    }

    public String getLockerId() {
        return lockerId;
    }

    public String getPackageId() {
        return packageId;
    }
}


interface LockerObserver {

    void onEvent(LockerEvent event);
}


class NotificationObserver
        implements LockerObserver {

    @Override
    public void onEvent(
            LockerEvent event) {

        System.out.println(
                "[NOTIFICATION] "
                        + event.getType()
                        + " lockerId="
                        + event.getLockerId());
    }
}


class AnalyticsObserver
        implements LockerObserver {

    @Override
    public void onEvent(
            LockerEvent event) {

        System.out.println(
                "[ANALYTICS] tracking "
                        + event.getType());
    }
}


class AuditObserver
        implements LockerObserver {

    @Override
    public void onEvent(
            LockerEvent event) {

        System.out.println(
                "[AUDIT] storing audit log "
                        + event.getType());
    }
}


// ======================================================
// EVENT PUBLISHER
// ======================================================

class LockerEventPublisher {

    private List<LockerObserver> observers;

    private ExecutorService executor;

    public LockerEventPublisher() {

        observers =
                new CopyOnWriteArrayList<>();

        executor =
                Executors.newFixedThreadPool(5);
    }

    public void subscribe(
            LockerObserver observer) {

        observers.add(observer);
    }

    public void publish(
            LockerEvent event) {

        for(LockerObserver observer
                : observers) {

            executor.submit(() ->
                    observer.onEvent(event));
        }
    }

    public void shutdown() {

        executor.shutdown();
    }
}


// ======================================================
// STATE PATTERN
// ======================================================

interface LockerState {

    void reserve(
            Locker locker,
            PackageItem packageItem,
            int expiryMinutes);

    void occupy(Locker locker);

    void pickup(
            Locker locker,
            String otp);

    void expire(Locker locker);

    String getStateName();
}


// ======================================================
// AVAILABLE STATE
// ======================================================

class AvailableState
        implements LockerState {

    @Override
    public void reserve(
            Locker locker,
            PackageItem packageItem,
            int expiryMinutes) {

        locker.setPackageItem(packageItem);

        locker.setOtp(
                UUID.randomUUID()
                        .toString()
                        .substring(0, 6));

        locker.setExpiryTime(
                LocalDateTime.now()
                        .plusMinutes(expiryMinutes));

        locker.setState(
                new ReservedState());

        locker.publishEvent(
                LockerEventType.RESERVED);

        System.out.println(
                "Locker RESERVED: "
                        + locker.getLockerId());
    }

    @Override
    public void occupy(Locker locker) {

        throw new RuntimeException(
                "Reserve first");
    }

    @Override
    public void pickup(
            Locker locker,
            String otp) {

        throw new RuntimeException(
                "Locker empty");
    }

    @Override
    public void expire(Locker locker) {

    }

    @Override
    public String getStateName() {

        return "AVAILABLE";
    }
}


// ======================================================
// RESERVED STATE
// ======================================================

class ReservedState
        implements LockerState {

    @Override
    public void reserve(
            Locker locker,
            PackageItem packageItem,
            int expiryMinutes) {

        throw new RuntimeException(
                "Already reserved");
    }

    @Override
    public void occupy(Locker locker) {

        locker.setState(
                new OccupiedState());

        locker.publishEvent(
                LockerEventType.OCCUPIED);

        System.out.println(
                "Locker OCCUPIED: "
                        + locker.getLockerId());
    }

    @Override
    public void pickup(
            Locker locker,
            String otp) {

        throw new RuntimeException(
                "Package not delivered yet");
    }

    @Override
    public void expire(Locker locker) {

        locker.setState(
                new ExpiredState());

        locker.publishEvent(
                LockerEventType.EXPIRED);

        locker.releaseResources();

        System.out.println(
                "Locker expired: "
                        + locker.getLockerId());
    }

    @Override
    public String getStateName() {

        return "RESERVED";
    }
}


// ======================================================
// OCCUPIED STATE
// ======================================================

class OccupiedState
        implements LockerState {

    @Override
    public void reserve(
            Locker locker,
            PackageItem packageItem,
            int expiryMinutes) {

        throw new RuntimeException(
                "Already occupied");
    }

    @Override
    public void occupy(Locker locker) {

        throw new RuntimeException(
                "Already occupied");
    }

    @Override
    public void pickup(
            Locker locker,
            String otp) {

        if(!locker.getOtp().equals(otp)) {

            throw new RuntimeException(
                    "Invalid OTP");
        }

        locker.publishEvent(
                LockerEventType.PICKED_UP);

        locker.releaseResources();

        locker.setState(
                new AvailableState());

        System.out.println(
                "Package picked from locker: "
                        + locker.getLockerId());
    }

    @Override
    public void expire(Locker locker) {

        locker.setState(
                new ExpiredState());

        locker.publishEvent(
                LockerEventType.EXPIRED);

        locker.releaseResources();

        System.out.println(
                "Locker expired: "
                        + locker.getLockerId());
    }

    @Override
    public String getStateName() {

        return "OCCUPIED";
    }
}


// ======================================================
// EXPIRED STATE
// ======================================================

class ExpiredState
        implements LockerState {

    @Override
    public void reserve(
            Locker locker,
            PackageItem packageItem,
            int expiryMinutes) {

        throw new RuntimeException(
                "Locker expired");
    }

    @Override
    public void occupy(Locker locker) {

        throw new RuntimeException(
                "Locker expired");
    }

    @Override
    public void pickup(
            Locker locker,
            String otp) {

        throw new RuntimeException(
                "Locker expired");
    }

    @Override
    public void expire(Locker locker) {

    }

    @Override
    public String getStateName() {

        return "EXPIRED";
    }
}


// ======================================================
// LOCKER
// ======================================================

class Locker {

    private String lockerId;

    private LockerSize size;

    private LockerState state;

    private PackageItem packageItem;

    private String otp;

    private LocalDateTime expiryTime;

    private ReentrantLock lock;

    private LockerEventPublisher publisher;

    public Locker(
            String lockerId,
            LockerSize size,
            LockerEventPublisher publisher) {

        this.lockerId = lockerId;

        this.size = size;

        this.publisher = publisher;

        this.state =
                new AvailableState();

        this.lock =
                new ReentrantLock();
    }

    public void reserve(
            PackageItem packageItem,
            int expiryMinutes) {

        lock.lock();

        try {

            state.reserve(
                    this,
                    packageItem,
                    expiryMinutes);

        } finally {

            lock.unlock();
        }
    }

    public void occupy() {

        lock.lock();

        try {

            state.occupy(this);

        } finally {

            lock.unlock();
        }
    }

    public void pickup(String otp) {

        lock.lock();

        try {

            state.pickup(this, otp);

        } finally {

            lock.unlock();
        }
    }

    public void expire() {

        lock.lock();

        try {

            state.expire(this);

        } finally {

            lock.unlock();
        }
    }

    public boolean isExpired() {

        return expiryTime != null
                &&
                LocalDateTime.now()
                        .isAfter(expiryTime);
    }

    public boolean isAvailable() {

        return state instanceof AvailableState;
    }

    public void releaseResources() {

        packageItem = null;

        otp = null;

        expiryTime = null;
    }

    public void publishEvent(
            LockerEventType type) {

        if(packageItem == null) {
            return;
        }

        publisher.publish(
                new LockerEvent(
                        type,
                        lockerId,
                        packageItem.getPackageId()));
    }

    public String getLockerId() {

        return lockerId;
    }

    public LockerSize getSize() {

        return size;
    }

    public LockerState getState() {

        return state;
    }

    public void setState(
            LockerState state) {

        this.state = state;
    }

    public PackageItem getPackageItem() {

        return packageItem;
    }

    public void setPackageItem(
            PackageItem packageItem) {

        this.packageItem = packageItem;
    }

    public String getOtp() {

        return otp;
    }

    public void setOtp(String otp) {

        this.otp = otp;
    }

    public LocalDateTime getExpiryTime() {

        return expiryTime;
    }

    public void setExpiryTime(
            LocalDateTime expiryTime) {

        this.expiryTime = expiryTime;
    }
}


// ======================================================
// REPOSITORY
// ======================================================

class LockerRepository {

    private List<Locker> lockers;

    public LockerRepository() {

        lockers =
                new CopyOnWriteArrayList<>();
    }

    public void addLocker(
            Locker locker) {

        lockers.add(locker);
    }

    public List<Locker> getAllLockers() {

        return lockers;
    }
}


// ======================================================
// STRATEGY PATTERN
// ======================================================

interface LockerAllocationStrategy {

    Locker allocateLocker(
            PackageItem packageItem,
            List<Locker> lockers);
}


// ======================================================
// BEST FIT STRATEGY
// ======================================================

class BestFitStrategy
        implements LockerAllocationStrategy {

    @Override
    public Locker allocateLocker(
            PackageItem packageItem,
            List<Locker> lockers) {

        Locker bestLocker = null;

        for(Locker locker : lockers) {

            if(!locker.isAvailable()) {
                continue;
            }

            if(canFit(
                    packageItem.getSize(),
                    locker.getSize())) {

                if(bestLocker == null
                        ||
                        locker.getSize().ordinal()
                        <
                        bestLocker.getSize()
                                .ordinal()) {

                    bestLocker = locker;
                }
            }
        }

        return bestLocker;
    }

    private boolean canFit(
            LockerSize packageSize,
            LockerSize lockerSize) {

        return lockerSize.ordinal()
                >= packageSize.ordinal();
    }
}


// ======================================================
// LOCKER MANAGER
// ======================================================

class LockerManager {

    private LockerRepository repository;

    private LockerAllocationStrategy strategy;

    public LockerManager(
            LockerRepository repository,

            LockerAllocationStrategy strategy) {

        this.repository = repository;

        this.strategy = strategy;
    }

    public Locker allocateLocker(
            PackageItem packageItem) {

        Locker locker =
                strategy.allocateLocker(
                        packageItem,
                        repository.getAllLockers());

        if(locker == null) {

            System.out.println(
                    "No locker available");

            return null;
        }

        locker.reserve(
                packageItem,
                1);

        return locker;
    }
}


// ======================================================
// EXPIRY WORKER
// ======================================================

class LockerExpiryWorker
        implements Runnable {

    private LockerRepository repository;

    public LockerExpiryWorker(
            LockerRepository repository) {

        this.repository = repository;
    }

    @Override
    public void run() {

        for(Locker locker
                : repository.getAllLockers()) {

            if(locker.isExpired()) {

                locker.expire();
            }
        }
    }
}


// ======================================================
// MAIN
// ======================================================

public class locker {

    public static void main(String[] args)
            throws Exception {

        // =====================================
        // OBSERVER SETUP
        // =====================================

        LockerEventPublisher publisher =
                new LockerEventPublisher();

        publisher.subscribe(
                new NotificationObserver());

        publisher.subscribe(
                new AnalyticsObserver());

        publisher.subscribe(
                new AuditObserver());


        // =====================================
        // REPOSITORY
        // =====================================

        LockerRepository repository =
                new LockerRepository();

        repository.addLocker(
                new Locker(
                        "L1",
                        LockerSize.SMALL,
                        publisher));

        repository.addLocker(
                new Locker(
                        "L2",
                        LockerSize.MEDIUM,
                        publisher));

        repository.addLocker(
                new Locker(
                        "L3",
                        LockerSize.LARGE,
                        publisher));


        // =====================================
        // STRATEGY + MANAGER
        // =====================================

        LockerAllocationStrategy strategy =
                new BestFitStrategy();

        LockerManager manager =
                new LockerManager(
                        repository,
                        strategy);


        // =====================================
        // EXECUTOR SERVICE
        // =====================================

        ScheduledExecutorService scheduler =
                Executors.newScheduledThreadPool(2);


        scheduler.scheduleAtFixedRate(
                new LockerExpiryWorker(
                        repository),

                0,
                5,
                TimeUnit.SECONDS);


        // =====================================
        // PACKAGE
        // =====================================

        PackageItem package1 =
                new PackageItem(
                        "P1",
                        LockerSize.SMALL,
                        "USER1");


        // =====================================
        // ALLOCATE
        // =====================================

        Locker locker =
                manager.allocateLocker(
                        package1);


        // =====================================
        // PACKAGE DELIVERED
        // =====================================

        locker.occupy();


        // =====================================
        // USER PICKUP
        // =====================================

        Thread.sleep(10000);

        locker.pickup(
                locker.getOtp());


        Thread.sleep(30000);

        scheduler.shutdown();

        publisher.shutdown();
    }
}