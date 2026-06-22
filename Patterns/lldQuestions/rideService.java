import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;


/*
    =======================================================
                UBER / RIDER MATCHING SYSTEM
    =======================================================

    FEATURES
    --------
    1. Rider requests ride
    2. Driver goes online/offline
    3. Nearest driver matching
    4. Strategy Pattern
    5. Thread Safe Driver Assignment
    6. Ride lifecycle
    7. Extensible Design
    8. Production-style service layer
*/


/* =======================================================
                        ENUMS
   ======================================================= */

enum DriverStatus {
    ONLINE,
    OFFLINE,
    BUSY
}

enum RideStatus {
    REQUESTED,
    MATCHED,
    ONGOING,
    COMPLETED,
    CANCELLED
}


/* =======================================================
                        LOCATION
   ======================================================= */

class Location {

    private final double latitude;
    private final double longitude;

    public Location(
            double latitude,
            double longitude
    ) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    /*
        Euclidean for simplicity.
        Production:
        Haversine formula / geo indexing.
    */

    public double distance(Location other) {

        double dx =
                this.latitude - other.latitude;

        double dy =
                this.longitude - other.longitude;

        return Math.sqrt(dx * dx + dy * dy);
    }
}


/* =======================================================
                        RIDER
   ======================================================= */

class Rider {

    private final String riderId;
    private final String name;

    public Rider(
            String riderId,
            String name
    ) {
        this.riderId = riderId;
        this.name = name;
    }

    public String getRiderId() {
        return riderId;
    }

    public String getName() {
        return name;
    }
}


/* =======================================================
                        VEHICLE
   ======================================================= */

class Vehicle {

    private final String vehicleId;
    private final String model;
    private final String plateNumber;

    public Vehicle(
            String vehicleId,
            String model,
            String plateNumber
    ) {
        this.vehicleId = vehicleId;
        this.model = model;
        this.plateNumber = plateNumber;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public String getModel() {
        return model;
    }

    public String getPlateNumber() {
        return plateNumber;
    }
}


/* =======================================================
                        DRIVER
   ======================================================= */

class Driver {

    private final String driverId;

    private final String name;

    private final Vehicle vehicle;

    private volatile DriverStatus status;

    private volatile Location location;

    /*
        THREAD SAFETY

        Lock per driver.
        Prevents same driver assigned twice.
    */

    private final ReentrantLock lock =
            new ReentrantLock();

    public Driver(
            String driverId,
            String name,
            Vehicle vehicle,
            Location location
    ) {
        this.driverId = driverId;
        this.name = name;
        this.vehicle = vehicle;
        this.location = location;
        this.status = DriverStatus.OFFLINE;
    }

    public String getDriverId() {
        return driverId;
    }

    public String getName() {
        return name;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public DriverStatus getStatus() {
        return status;
    }

    public void setStatus(DriverStatus status) {
        this.status = status;
    }

    public Location getLocation() {
        return location;
    }

    public void updateLocation(
            Location location
    ) {
        this.location = location;
    }

    public ReentrantLock getLock() {
        return lock;
    }
}


/* =======================================================
                            RIDE
   ======================================================= */

class Ride {

    private final String rideId;

    private final Rider rider;

    private final Driver driver;

    private final Location pickup;

    private final Location destination;

    private RideStatus status;

    public Ride(
            String rideId,
            Rider rider,
            Driver driver,
            Location pickup,
            Location destination
    ) {
        this.rideId = rideId;
        this.rider = rider;
        this.driver = driver;
        this.pickup = pickup;
        this.destination = destination;
        this.status = RideStatus.MATCHED;
    }

    public String getRideId() {
        return rideId;
    }

    public Rider getRider() {
        return rider;
    }

    public Driver getDriver() {
        return driver;
    }

    public RideStatus getStatus() {
        return status;
    }

    public void setStatus(
            RideStatus status
    ) {
        this.status = status;
    }
}


/* =======================================================
                    MATCHING STRATEGY
   ======================================================= */

interface MatchingStrategy {

    Driver findDriver(
            List<Driver> drivers,
            Location pickup
    );
}


/* =======================================================
                NEAREST DRIVER STRATEGY
   ======================================================= */

class NearestDriverStrategy
        implements MatchingStrategy {

    @Override
    public Driver findDriver(
            List<Driver> drivers,
            Location pickup
    ) {

        Driver nearestDriver = null;

        double minDistance =
                Double.MAX_VALUE;

        for(Driver driver : drivers) {

            if(driver.getStatus()
                    != DriverStatus.ONLINE) {

                continue;
            }

            double distance =
                    driver.getLocation()
                            .distance(pickup);

            if(distance < minDistance) {

                minDistance = distance;

                nearestDriver = driver;
            }
        }

        return nearestDriver;
    }
}


/* =======================================================
                    DRIVER SERVICE
   ======================================================= */

class DriverService {

    private final Map<String, Driver>
            drivers = new ConcurrentHashMap<>();

    public void registerDriver(
            Driver driver
    ) {
        drivers.put(
                driver.getDriverId(),
                driver
        );
    }

    public void setDriverOnline(
            String driverId
    ) {

        Driver driver =
                drivers.get(driverId);

        if(driver != null) {
            driver.setStatus(
                    DriverStatus.ONLINE
            );
        }
    }

    public void setDriverOffline(
            String driverId
    ) {

        Driver driver =
                drivers.get(driverId);

        if(driver != null) {
            driver.setStatus(
                    DriverStatus.OFFLINE
            );
        }
    }

    public void updateLocation(
            String driverId,
            Location location
    ) {

        Driver driver =
                drivers.get(driverId);

        if(driver != null) {
            driver.updateLocation(
                    location
            );
        }
    }

    public List<Driver> getAllDrivers() {
        return new ArrayList<>(
                drivers.values()
        );
    }
}


/* =======================================================
                    MATCHING SERVICE
   ======================================================= */

class MatchingService {

    private final MatchingStrategy
            matchingStrategy;

    public MatchingService(
            MatchingStrategy matchingStrategy
    ) {
        this.matchingStrategy =
                matchingStrategy;
    }

    public Driver matchDriver(
            List<Driver> drivers,
            Location pickup
    ) {

        Driver driver =
                matchingStrategy.findDriver(
                        drivers,
                        pickup
                );

        if(driver == null) {
            return null;
        }

        /*
            THREAD SAFETY

            Prevent same driver assigned twice.
        */

        ReentrantLock lock =
                driver.getLock();

        lock.lock();

        try {

            if(driver.getStatus()
                    != DriverStatus.ONLINE) {

                return null;
            }

            driver.setStatus(
                    DriverStatus.BUSY
            );

            return driver;

        } finally {
            lock.unlock();
        }
    }
}


/* =======================================================
                    RIDE SERVICE
   ======================================================= */

class RideService {

    private final DriverService
            driverService;

    private final MatchingService
            matchingService;

    private final Map<String, Ride>
            rides = new ConcurrentHashMap<>();

    public RideService(
            DriverService driverService,
            MatchingService matchingService
    ) {
        this.driverService =
                driverService;

        this.matchingService =
                matchingService;
    }

    public Ride requestRide(
            Rider rider,
            Location pickup,
            Location destination
    ) {

        Driver driver =
                matchingService.matchDriver(
                        driverService.getAllDrivers(),
                        pickup
                );

        if(driver == null) {

            System.out.println(
                    "No drivers available"
            );

            return null;
        }

        Ride ride =
                new Ride(
                        UUID.randomUUID().toString(),
                        rider,
                        driver,
                        pickup,
                        destination
                );

        rides.put(
                ride.getRideId(),
                ride
        );

        System.out.println(
                "Ride matched with driver: "
                        + driver.getName()
        );

        return ride;
    }

    public void startRide(
            String rideId
    ) {

        Ride ride = rides.get(rideId);

        if(ride != null) {

            ride.setStatus(
                    RideStatus.ONGOING
            );

            System.out.println(
                    "Ride started"
            );
        }
    }

    public void endRide(
            String rideId
    ) {

        Ride ride = rides.get(rideId);

        if(ride == null) {
            return;
        }

        ride.setStatus(
                RideStatus.COMPLETED
        );

        ride.getDriver().setStatus(
                DriverStatus.ONLINE
        );

        System.out.println(
                "Ride completed"
        );
    }
}


/* =======================================================
                        MAIN
   ======================================================= */

public class Main {

    public static void main(String[] args) {

        DriverService driverService =
                new DriverService();

        /*
            REGISTER DRIVERS
        */

        Driver d1 =
                new Driver(
                        "D1",
                        "Rahul",

                        new Vehicle(
                                "V1",
                                "Swift",
                                "KA01"
                        ),

                        new Location(10, 10)
                );

        Driver d2 =
                new Driver(
                        "D2",
                        "Aman",

                        new Vehicle(
                                "V2",
                                "i20",
                                "KA02"
                        ),

                        new Location(20, 20)
                );

        driverService.registerDriver(d1);
        driverService.registerDriver(d2);

        driverService.setDriverOnline("D1");
        driverService.setDriverOnline("D2");

        /*
            MATCHING STRATEGY
        */

        MatchingStrategy strategy =
                new NearestDriverStrategy();

        MatchingService matchingService =
                new MatchingService(strategy);

        RideService rideService =
                new RideService(
                        driverService,
                        matchingService
                );

        /*
            RIDER
        */

        Rider rider =
                new Rider(
                        "R1",
                        "Mineesha"
                );

        /*
            REQUEST RIDE
        */

        Ride ride =
                rideService.requestRide(
                        rider,
                        new Location(11, 11),
                        new Location(30, 30)
                );

        if(ride != null) {

            rideService.startRide(
                    ride.getRideId()
            );

            rideService.endRide(
                    ride.getRideId()
            );
        }
    }
}
```
