# Hotel Management System — SDE-2 LLD (Java)

```java id="3lf3s8"
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;


/*
    =======================================================
                    HOTEL MANAGEMENT SYSTEM
    =======================================================

    FEATURES
    --------
    1. Search rooms
    2. Book room
    3. Cancel booking
    4. Check-in
    5. Check-out
    6. Room types
    7. Pricing strategy
    8. Thread-safe booking
    9. Extensible design
*/


/* =======================================================
                        ENUMS
   ======================================================= */

enum RoomType {
    STANDARD,
    DELUXE,
    SUITE
}

enum RoomStatus {
    AVAILABLE,
    OCCUPIED,
    MAINTENANCE
}

enum BookingStatus {
    BOOKED,
    CANCELLED,
    CHECKED_IN,
    CHECKED_OUT
}


/* =======================================================
                        GUEST
   ======================================================= */

class Guest {

    private final String guestId;

    private final String name;

    public Guest(
            String guestId,
            String name
    ) {
        this.guestId = guestId;
        this.name = name;
    }

    public String getGuestId() {
        return guestId;
    }

    public String getName() {
        return name;
    }
}


/* =======================================================
                        ROOM
   ======================================================= */

class Room {

    private final int roomNumber;

    private final RoomType roomType;

    private volatile RoomStatus status;

    private final double basePrice;

    /*
        THREAD SAFETY

        Prevent double booking.
    */

    private final ReentrantLock lock =
            new ReentrantLock();

    public Room(
            int roomNumber,
            RoomType roomType,
            double basePrice
    ) {
        this.roomNumber = roomNumber;
        this.roomType = roomType;
        this.basePrice = basePrice;
        this.status = RoomStatus.AVAILABLE;
    }

    public int getRoomNumber() {
        return roomNumber;
    }

    public RoomType getRoomType() {
        return roomType;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public void setStatus(
            RoomStatus status
    ) {
        this.status = status;
    }

    public double getBasePrice() {
        return basePrice;
    }

    public ReentrantLock getLock() {
        return lock;
    }
}


/* =======================================================
                        BOOKING
   ======================================================= */

class Booking {

    private final String bookingId;

    private final Guest guest;

    private final Room room;

    private final LocalDate checkInDate;

    private final LocalDate checkOutDate;

    private BookingStatus status;

    private final double totalPrice;

    public Booking(
            String bookingId,
            Guest guest,
            Room room,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            double totalPrice
    ) {

        this.bookingId = bookingId;

        this.guest = guest;

        this.room = room;

        this.checkInDate = checkInDate;

        this.checkOutDate = checkOutDate;

        this.totalPrice = totalPrice;

        this.status = BookingStatus.BOOKED;
    }

    public String getBookingId() {
        return bookingId;
    }

    public Guest getGuest() {
        return guest;
    }

    public Room getRoom() {
        return room;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(
            BookingStatus status
    ) {
        this.status = status;
    }

    public double getTotalPrice() {
        return totalPrice;
    }
}


/* =======================================================
                    PRICING STRATEGY
   ======================================================= */

interface PricingStrategy {

    double calculatePrice(
            Room room,
            long days
    );
}


/* =======================================================
                NORMAL PRICING STRATEGY
   ======================================================= */

class NormalPricingStrategy
        implements PricingStrategy {

    @Override
    public double calculatePrice(
            Room room,
            long days
    ) {

        return room.getBasePrice()
                * days;
    }
}


/* =======================================================
                    ROOM SERVICE
   ======================================================= */

class RoomService {

    private final Map<Integer, Room>
            roomMap =
            new ConcurrentHashMap<>();

    public void addRoom(
            Room room
    ) {

        roomMap.put(
                room.getRoomNumber(),
                room
        );
    }

    public List<Room> searchRooms(
            RoomType roomType
    ) {

        List<Room> result =
                new ArrayList<>();

        for(Room room : roomMap.values()) {

            if(room.getRoomType()
                    == roomType
                    &&
                    room.getStatus()
                            == RoomStatus.AVAILABLE) {

                result.add(room);
            }
        }

        return result;
    }

    public Room getRoom(
            int roomNumber
    ) {
        return roomMap.get(roomNumber);
    }
}


/* =======================================================
                    BOOKING SERVICE
   ======================================================= */

class BookingService {

    private final RoomService roomService;

    private final PricingStrategy
            pricingStrategy;

    private final Map<String, Booking>
            bookingMap =
            new ConcurrentHashMap<>();

    public BookingService(
            RoomService roomService,
            PricingStrategy pricingStrategy
    ) {

        this.roomService = roomService;

        this.pricingStrategy =
                pricingStrategy;
    }

    public Booking bookRoom(
            Guest guest,
            int roomNumber,
            LocalDate checkIn,
            LocalDate checkOut
    ) {

        Room room =
                roomService.getRoom(roomNumber);

        if(room == null) {

            System.out.println(
                    "Room not found"
            );

            return null;
        }

        /*
            THREAD SAFETY

            Prevent double booking.
        */

        ReentrantLock lock =
                room.getLock();

        lock.lock();

        try {

            if(room.getStatus()
                    != RoomStatus.AVAILABLE) {

                System.out.println(
                        "Room unavailable"
                );

                return null;
            }

            long days =
                    checkOut.toEpochDay()
                            - checkIn.toEpochDay();

            double totalPrice =
                    pricingStrategy
                            .calculatePrice(
                                    room,
                                    days
                            );

            Booking booking =
                    new Booking(
                            UUID.randomUUID()
                                    .toString(),

                            guest,

                            room,

                            checkIn,

                            checkOut,

                            totalPrice
                    );

            bookingMap.put(
                    booking.getBookingId(),
                    booking
            );

            room.setStatus(
                    RoomStatus.OCCUPIED
            );

            System.out.println(
                    "Booking successful"
            );

            return booking;

        } finally {
            lock.unlock();
        }
    }

    public void cancelBooking(
            String bookingId
    ) {

        Booking booking =
                bookingMap.get(bookingId);

        if(booking == null) {
            return;
        }

        Room room =
                booking.getRoom();

        ReentrantLock lock =
                room.getLock();

        lock.lock();

        try {

            booking.setStatus(
                    BookingStatus.CANCELLED
            );

            room.setStatus(
                    RoomStatus.AVAILABLE
            );

            System.out.println(
                    "Booking cancelled"
            );

        } finally {
            lock.unlock();
        }
    }

    public void checkIn(
            String bookingId
    ) {

        Booking booking =
                bookingMap.get(bookingId);

        if(booking != null) {

            booking.setStatus(
                    BookingStatus.CHECKED_IN
            );

            System.out.println(
                    "Guest checked in"
            );
        }
    }

    public void checkOut(
            String bookingId
    ) {

        Booking booking =
                bookingMap.get(bookingId);

        if(booking == null) {
            return;
        }

        Room room =
                booking.getRoom();

        ReentrantLock lock =
                room.getLock();

        lock.lock();

        try {

            booking.setStatus(
                    BookingStatus.CHECKED_OUT
            );

            room.setStatus(
                    RoomStatus.AVAILABLE
            );

            System.out.println(
                    "Guest checked out"
            );

        } finally {
            lock.unlock();
        }
    }
}


/* =======================================================
                        MAIN
   ======================================================= */

public class Main {

    public static void main(String[] args) {

        RoomService roomService =
                new RoomService();

        /*
            ADD ROOMS
        */

        roomService.addRoom(
                new Room(
                        101,
                        RoomType.STANDARD,
                        2000
                )
        );

        roomService.addRoom(
                new Room(
                        102,
                        RoomType.DELUXE,
                        5000
                )
        );

        roomService.addRoom(
                new Room(
                        103,
                        RoomType.SUITE,
                        10000
                )
        );

        PricingStrategy pricingStrategy =
                new NormalPricingStrategy();

        BookingService bookingService =
                new BookingService(
                        roomService,
                        pricingStrategy
                );

        /*
            SEARCH
        */

        List<Room> rooms =
                roomService.searchRooms(
                        RoomType.DELUXE
                );

        System.out.println(
                "Available deluxe rooms: "
                        + rooms.size()
        );

        /*
            GUEST
        */

        Guest guest =
                new Guest(
                        "G1",
                        "Mineesha"
                );

        /*
            BOOK
        */

        Booking booking =
                bookingService.bookRoom(
                        guest,
                        102,
                        LocalDate.now(),
                        LocalDate.now().plusDays(3)
                );

        if(booking != null) {

            bookingService.checkIn(
                    booking.getBookingId()
            );

            bookingService.checkOut(
                    booking.getBookingId()
            );
        }
    }
}
```
