import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;


// ======================================================
// ENUMS
// ======================================================

enum LockerSize {
    SMALL,
    MEDIUM,
    LARGE
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

        System.out.println(
                "Locker RESERVED: "
                        + locker.getLockerId()
                        + " OTP: "
                        + locker.getOtp());
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
                "Locker already reserved");
    }

    @Override
    public void occupy(Locker locker) {

        locker.setState(
                new OccupiedState());

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

        System.out.println(
                "Locker EXPIRED: "
                        + locker.getLockerId());

        locker.releaseResources();
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
                "Locker occupied");
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

        System.out.println(
                "Package picked from locker: "
                        + locker.getLockerId());

        locker.releaseResources();

        locker.setState(
                new AvailableState());
    }

    @Override
    public void expire(Locker locker) {

        locker.setState(
                new ExpiredState());

        System.out.println(
                "Locker EXPIRED: "
                        + locker.getLockerId());

        locker.releaseResources();
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

    public Locker(
            String lockerId,
            LockerSize size) {

        this.lockerId = lockerId;
        this.size = size;

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

        state = new AvailableState();

        System.out.println(
                "Locker AVAILABLE again: "
                        + lockerId);
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

        List<Locker> lockers =
                repository.getAllLockers();

        for(Locker locker : lockers) {

            if(locker.isExpired()) {

                locker.expire();
            }
        }
    }
}


// ======================================================
// MAIN
// ======================================================

public class Main {

    public static void main(String[] args)
            throws Exception {

        LockerRepository repository =
                new LockerRepository();

        repository.addLocker(
                new Locker(
                        "L1",
                        LockerSize.SMALL));

        repository.addLocker(
                new Locker(
                        "L2",
                        LockerSize.MEDIUM));

        repository.addLocker(
                new Locker(
                        "L3",
                        LockerSize.LARGE));


        LockerAllocationStrategy strategy =
                new BestFitStrategy();

        LockerManager manager =
                new LockerManager(
                        repository,
                        strategy);


        // =====================================
        // EXECUTOR SERVICE
        // =====================================

        ScheduledExecutorService
                scheduler =

                Executors
                        .newScheduledThreadPool(2);


        // expiry worker every 5 sec
        scheduler.scheduleAtFixedRate(
                new LockerExpiryWorker(
                        repository),

                0,
                5,
                TimeUnit.SECONDS);


        // =====================================
        // ALLOCATE PACKAGE
        // =====================================

        PackageItem package1 =
                new PackageItem(
                        "P1",
                        LockerSize.SMALL,
                        "USER1");


        Locker locker =
                manager.allocateLocker(
                        package1);


        // package delivered
        locker.occupy();


        // =====================================
        // SIMULATE USER PICKUP
        // =====================================

        Thread.sleep(10000);

        try {

            locker.pickup(
                    locker.getOtp());

        } catch(Exception e) {

            System.out.println(
                    e.getMessage());
        }


        Thread.sleep(60000);

        scheduler.shutdown();
    }
}