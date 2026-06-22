# Parking Lot — SDE-2 LLD (Java)

```java id="2d9v5k"
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;


/*
    =========================================================
                    PARKING LOT SYSTEM
    =========================================================

    FEATURES
    --------
    1. Multiple floors
    2. Multiple spot types
    3. Park vehicle
    4. Unpark vehicle
    5. Ticket generation
    6. Fee calculation
    7. Thread-safe parking
    8. Strategy Pattern
    9. Extensible design
*/


/* =========================================================
                        ENUMS
   ========================================================= */

enum VehicleType {
    BIKE,
    CAR,
    TRUCK
}

enum SpotType {
    SMALL,
    MEDIUM,
    LARGE
}

enum SpotStatus {
    AVAILABLE,
    OCCUPIED
}


/* =========================================================
                        VEHICLE
   ========================================================= */

abstract class Vehicle {

    private final String vehicleNumber;

    private final VehicleType vehicleType;

    public Vehicle(
            String vehicleNumber,
            VehicleType vehicleType
    ) {

        this.vehicleNumber = vehicleNumber;
        this.vehicleType = vehicleType;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }
}


class Bike extends Vehicle {

    public Bike(String vehicleNumber) {
        super(
                vehicleNumber,
                VehicleType.BIKE
        );
    }
}

class Car extends Vehicle {

    public Car(String vehicleNumber) {
        super(
                vehicleNumber,
                VehicleType.CAR
        );
    }
}

class Truck extends Vehicle {

    public Truck(String vehicleNumber) {
        super(
                vehicleNumber,
                VehicleType.TRUCK
        );
    }
}


/* =========================================================
                    PARKING SPOT
   ========================================================= */

class ParkingSpot {

    private final int spotId;

    private final SpotType spotType;

    private volatile SpotStatus status;

    private Vehicle parkedVehicle;

    /*
        THREAD SAFETY

        Lock per spot.
    */

    private final ReentrantLock lock =
            new ReentrantLock();

    public ParkingSpot(
            int spotId,
            SpotType spotType
    ) {

        this.spotId = spotId;

        this.spotType = spotType;

        this.status = SpotStatus.AVAILABLE;
    }

    public int getSpotId() {
        return spotId;
    }

    public SpotType getSpotType() {
        return spotType;
    }

    public SpotStatus getStatus() {
        return status;
    }

    public void setStatus(
            SpotStatus status
    ) {
        this.status = status;
    }

    public Vehicle getParkedVehicle() {
        return parkedVehicle;
    }

    public void parkVehicle(
            Vehicle vehicle
    ) {
        this.parkedVehicle = vehicle;
    }

    public void removeVehicle() {
        parkedVehicle = null;
    }

    public ReentrantLock getLock() {
        return lock;
    }
}


/* =========================================================
                    PARKING FLOOR
   ========================================================= */

class ParkingFloor {

    private final int floorNumber;

    private final List<ParkingSpot>
            parkingSpots =
            new ArrayList<>();

    public ParkingFloor(
            int floorNumber
    ) {
        this.floorNumber = floorNumber;
    }

    public void addSpot(
            ParkingSpot spot
    ) {
        parkingSpots.add(spot);
    }

    public List<ParkingSpot> getParkingSpots() {
        return parkingSpots;
    }

    public int getFloorNumber() {
        return floorNumber;
    }
}


/* =========================================================
                        TICKET
   ========================================================= */

class Ticket {

    private final String ticketId;

    private final Vehicle vehicle;

    private final ParkingSpot parkingSpot;

    private final LocalDateTime entryTime;

    public Ticket(
            String ticketId,
            Vehicle vehicle,
            ParkingSpot parkingSpot
    ) {

        this.ticketId = ticketId;

        this.vehicle = vehicle;

        this.parkingSpot = parkingSpot;

        this.entryTime = LocalDateTime.now();
    }

    public String getTicketId() {
        return ticketId;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public ParkingSpot getParkingSpot() {
        return parkingSpot;
    }

    public LocalDateTime getEntryTime() {
        return entryTime;
    }
}


/* =========================================================
                    FEE STRATEGY
   ========================================================= */

interface FeeStrategy {

    double calculateFee(
            Ticket ticket
    );
}


/* =========================================================
                HOURLY FEE STRATEGY
   ========================================================= */

class HourlyFeeStrategy
        implements FeeStrategy {

    @Override
    public double calculateFee(
            Ticket ticket
    ) {

        long hours =
                Math.max(
                        1,

                        Duration.between(
                                ticket.getEntryTime(),
                                LocalDateTime.now()
                        ).toHours()
                );

        switch(
                ticket.getVehicle()
                        .getVehicleType()
        ) {

            case BIKE:
                return hours * 10;

            case CAR:
                return hours * 20;

            case TRUCK:
                return hours * 50;

            default:
                return 0;
        }
    }
}


/* =========================================================
                SPOT ASSIGNMENT STRATEGY
   ========================================================= */

interface SpotAssignmentStrategy {

    ParkingSpot assignSpot(
            List<ParkingFloor> floors,
            Vehicle vehicle
    );
}


/* =========================================================
            NEAREST AVAILABLE SPOT STRATEGY
   ========================================================= */

class NearestSpotStrategy
        implements SpotAssignmentStrategy {

    @Override
    public ParkingSpot assignSpot(
            List<ParkingFloor> floors,
            Vehicle vehicle
    ) {

        SpotType requiredSpot =
                getRequiredSpot(vehicle);

        for(ParkingFloor floor : floors) {

            for(ParkingSpot spot
                    : floor.getParkingSpots()) {

                if(spot.getStatus()
                        == SpotStatus.AVAILABLE
                        &&
                        spot.getSpotType()
                                == requiredSpot) {

                    return spot;
                }
            }
        }

        return null;
    }

    private SpotType getRequiredSpot(
            Vehicle vehicle
    ) {

        switch(vehicle.getVehicleType()) {

            case BIKE:
                return SpotType.SMALL;

            case CAR:
                return SpotType.MEDIUM;

            case TRUCK:
                return SpotType.LARGE;

            default:
                return SpotType.MEDIUM;
        }
    }
}


/* =========================================================
                    PARKING LOT
   ========================================================= */

class ParkingLot {

    private final List<ParkingFloor>
            floors =
            new ArrayList<>();

    private final SpotAssignmentStrategy
            assignmentStrategy;

    private final FeeStrategy feeStrategy;

    private final Map<String, Ticket>
            activeTickets =
            new ConcurrentHashMap<>();

    public ParkingLot(
            SpotAssignmentStrategy
                    assignmentStrategy,

            FeeStrategy feeStrategy
    ) {

        this.assignmentStrategy =
                assignmentStrategy;

        this.feeStrategy =
                feeStrategy;
    }

    public void addFloor(
            ParkingFloor floor
    ) {
        floors.add(floor);
    }

    /*
        PARK VEHICLE
    */

    public Ticket parkVehicle(
            Vehicle vehicle
    ) {

        ParkingSpot spot =
                assignmentStrategy.assignSpot(
                        floors,
                        vehicle
                );

        if(spot == null) {

            System.out.println(
                    "No parking spot available"
            );

            return null;
        }

        /*
            THREAD SAFETY

            Prevent double parking.
        */

        ReentrantLock lock =
                spot.getLock();

        lock.lock();

        try {

            if(spot.getStatus()
                    == SpotStatus.OCCUPIED) {

                return null;
            }

            spot.parkVehicle(vehicle);

            spot.setStatus(
                    SpotStatus.OCCUPIED
            );

            Ticket ticket =
                    new Ticket(
                            UUID.randomUUID()
                                    .toString(),

                            vehicle,

                            spot
                    );

            activeTickets.put(
                    ticket.getTicketId(),
                    ticket
            );

            System.out.println(
                    "Vehicle parked at spot: "
                            + spot.getSpotId()
            );

            return ticket;

        } finally {
            lock.unlock();
        }
    }

    /*
        UNPARK VEHICLE
    */

    public void unparkVehicle(
            String ticketId
    ) {

        Ticket ticket =
                activeTickets.get(ticketId);

        if(ticket == null) {

            System.out.println(
                    "Invalid ticket"
            );

            return;
        }

        ParkingSpot spot =
                ticket.getParkingSpot();

        ReentrantLock lock =
                spot.getLock();

        lock.lock();

        try {

            double fee =
                    feeStrategy.calculateFee(
                            ticket
                    );

            spot.removeVehicle();

            spot.setStatus(
                    SpotStatus.AVAILABLE
            );

            activeTickets.remove(ticketId);

            System.out.println(
                    "Vehicle unparked"
            );

            System.out.println(
                    "Parking fee: "
                            + fee
            );

        } finally {
            lock.unlock();
        }
    }
}


/* =========================================================
                            MAIN
   ========================================================= */

public class Main {

    public static void main(String[] args)
            throws InterruptedException {

        /*
            STRATEGIES
        */

        SpotAssignmentStrategy
                assignmentStrategy =
                new NearestSpotStrategy();

        FeeStrategy feeStrategy =
                new HourlyFeeStrategy();

        ParkingLot parkingLot =
                new ParkingLot(
                        assignmentStrategy,
                        feeStrategy
                );

        /*
            FLOOR 1
        */

        ParkingFloor floor1 =
                new ParkingFloor(1);

        floor1.addSpot(
                new ParkingSpot(
                        1,
                        SpotType.SMALL
                )
        );

        floor1.addSpot(
                new ParkingSpot(
                        2,
                        SpotType.MEDIUM
                )
        );

        floor1.addSpot(
                new ParkingSpot(
                        3,
                        SpotType.LARGE
                )
        );

        parkingLot.addFloor(floor1);

        /*
            VEHICLES
        */

        Vehicle car =
                new Car("KA01AB1234");

        /*
            PARK
        */

        Ticket ticket =
                parkingLot.parkVehicle(car);

        Thread.sleep(2000);

        /*
            UNPARK
        */

        if(ticket != null) {

            parkingLot.unparkVehicle(
                    ticket.getTicketId()
            );
        }
    }
}
```
