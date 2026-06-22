# BookMyShow — SDE-2 LLD (Java)

```java id="x2m9qv"
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;


/*
    =========================================================
                    BOOK MY SHOW SYSTEM
    =========================================================

    FEATURES
    --------
    1. Movies
    2. Theatres
    3. Screens
    4. Shows
    5. Seat booking
    6. Payment simulation
    7. Thread-safe seat booking
    8. Booking cancellation
    9. Strategy Pattern
    10. Extensible design
*/


/* =========================================================
                        ENUMS
   ========================================================= */

enum SeatType {
    REGULAR,
    PREMIUM,
    RECLINER
}

enum SeatStatus {
    AVAILABLE,
    BOOKED
}

enum BookingStatus {
    CREATED,
    CONFIRMED,
    CANCELLED
}


/* =========================================================
                        MOVIE
   ========================================================= */

class Movie {

    private final String movieId;

    private final String name;

    private final int durationMinutes;

    public Movie(
            String movieId,
            String name,
            int durationMinutes
    ) {

        this.movieId = movieId;

        this.name = name;

        this.durationMinutes =
                durationMinutes;
    }

    public String getMovieId() {
        return movieId;
    }

    public String getName() {
        return name;
    }
}


/* =========================================================
                        THEATRE
   ========================================================= */

class Theatre {

    private final String theatreId;

    private final String name;

    private final List<Screen> screens =
            new ArrayList<>();

    public Theatre(
            String theatreId,
            String name
    ) {

        this.theatreId = theatreId;

        this.name = name;
    }

    public void addScreen(
            Screen screen
    ) {
        screens.add(screen);
    }

    public List<Screen> getScreens() {
        return screens;
    }
}


/* =========================================================
                        SCREEN
   ========================================================= */

class Screen {

    private final String screenId;

    private final String name;

    private final List<Seat> seats =
            new ArrayList<>();

    public Screen(
            String screenId,
            String name
    ) {

        this.screenId = screenId;

        this.name = name;
    }

    public void addSeat(
            Seat seat
    ) {
        seats.add(seat);
    }

    public List<Seat> getSeats() {
        return seats;
    }
}


/* =========================================================
                        SEAT
   ========================================================= */

class Seat {

    private final String seatId;

    private final SeatType seatType;

    private volatile SeatStatus status;

    /*
        THREAD SAFETY

        Lock per seat.
    */

    private final ReentrantLock lock =
            new ReentrantLock();

    public Seat(
            String seatId,
            SeatType seatType
    ) {

        this.seatId = seatId;

        this.seatType = seatType;

        this.status = SeatStatus.AVAILABLE;
    }

    public String getSeatId() {
        return seatId;
    }

    public SeatType getSeatType() {
        return seatType;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public void setStatus(
            SeatStatus status
    ) {
        this.status = status;
    }

    public ReentrantLock getLock() {
        return lock;
    }
}


/* =========================================================
                        SHOW
   ========================================================= */

class Show {

    private final String showId;

    private final Movie movie;

    private final Screen screen;

    private final LocalDateTime startTime;

    public Show(
            String showId,
            Movie movie,
            Screen screen,
            LocalDateTime startTime
    ) {

        this.showId = showId;

        this.movie = movie;

        this.screen = screen;

        this.startTime = startTime;
    }

    public String getShowId() {
        return showId;
    }

    public Movie getMovie() {
        return movie;
    }

    public Screen getScreen() {
        return screen;
    }
}


/* =========================================================
                        USER
   ========================================================= */

class User {

    private final String userId;

    private final String name;

    public User(
            String userId,
            String name
    ) {

        this.userId = userId;

        this.name = name;
    }

    public String getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }
}


/* =========================================================
                        BOOKING
   ========================================================= */

class Booking {

    private final String bookingId;

    private final User user;

    private final Show show;

    private final List<Seat> seats;

    private BookingStatus status;

    private final double amount;

    public Booking(
            String bookingId,
            User user,
            Show show,
            List<Seat> seats,
            double amount
    ) {

        this.bookingId = bookingId;

        this.user = user;

        this.show = show;

        this.seats = seats;

        this.amount = amount;

        this.status = BookingStatus.CREATED;
    }

    public String getBookingId() {
        return bookingId;
    }

    public List<Seat> getSeats() {
        return seats;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(
            BookingStatus status
    ) {
        this.status = status;
    }

    public double getAmount() {
        return amount;
    }
}


/* =========================================================
                    PRICING STRATEGY
   ========================================================= */

interface PricingStrategy {

    double calculatePrice(
            List<Seat> seats
    );
}


/* =========================================================
                SIMPLE PRICING STRATEGY
   ========================================================= */

class SimplePricingStrategy
        implements PricingStrategy {

    @Override
    public double calculatePrice(
            List<Seat> seats
    ) {

        double total = 0;

        for(Seat seat : seats) {

            switch(seat.getSeatType()) {

                case REGULAR:
                    total += 200;
                    break;

                case PREMIUM:
                    total += 400;
                    break;

                case RECLINER:
                    total += 700;
                    break;
            }
        }

        return total;
    }
}


/* =========================================================
                    PAYMENT SERVICE
   ========================================================= */

class PaymentService {

    public boolean processPayment(
            double amount
    ) {

        System.out.println(
                "Payment successful: "
                        + amount
        );

        return true;
    }
}


/* =========================================================
                    BOOKING SERVICE
   ========================================================= */

class BookingService {

    private final PricingStrategy
            pricingStrategy;

    private final PaymentService
            paymentService;

    private final Map<String, Booking>
            bookings =
            new ConcurrentHashMap<>();

    public BookingService(
            PricingStrategy pricingStrategy,
            PaymentService paymentService
    ) {

        this.pricingStrategy =
                pricingStrategy;

        this.paymentService =
                paymentService;
    }

    /*
        BOOK SEATS
    */

    public Booking bookSeats(
            User user,
            Show show,
            List<String> seatIds
    ) {

        List<Seat> selectedSeats =
                new ArrayList<>();

        /*
            Find seats
        */

        for(Seat seat
                : show.getScreen().getSeats()) {

            if(seatIds.contains(
                    seat.getSeatId()
            )) {

                selectedSeats.add(seat);
            }
        }

        /*
            SORT LOCKS

            Prevent deadlock.
        */

        selectedSeats.sort(
                Comparator.comparing(
                        Seat::getSeatId
                )
        );

        /*
            LOCK ALL SEATS
        */

        for(Seat seat : selectedSeats) {
            seat.getLock().lock();
        }

        try {

            /*
                CHECK AVAILABILITY
            */

            for(Seat seat : selectedSeats) {

                if(seat.getStatus()
                        == SeatStatus.BOOKED) {

                    System.out.println(
                            "Seat already booked"
                    );

                    return null;
                }
            }

            /*
                PRICE
            */

            double amount =
                    pricingStrategy
                            .calculatePrice(
                                    selectedSeats
                            );

            /*
                PAYMENT
            */

            boolean success =
                    paymentService
                            .processPayment(
                                    amount
                            );

            if(!success) {

                return null;
            }

            /*
                BOOK SEATS
            */

            for(Seat seat : selectedSeats) {

                seat.setStatus(
                        SeatStatus.BOOKED
                );
            }

            Booking booking =
                    new Booking(
                            UUID.randomUUID()
                                    .toString(),

                            user,

                            show,

                            selectedSeats,

                            amount
                    );

            booking.setStatus(
                    BookingStatus.CONFIRMED
            );

            bookings.put(
                    booking.getBookingId(),
                    booking
            );

            System.out.println(
                    "Booking successful"
            );

            return booking;

        } finally {

            /*
                RELEASE LOCKS
            */

            for(Seat seat : selectedSeats) {
                seat.getLock().unlock();
            }
        }
    }

    /*
        CANCEL BOOKING
    */

    public void cancelBooking(
            String bookingId
    ) {

        Booking booking =
                bookings.get(bookingId);

        if(booking == null) {
            return;
        }

        List<Seat> seats =
                booking.getSeats();

        for(Seat seat : seats) {
            seat.getLock().lock();
        }

        try {

            for(Seat seat : seats) {

                seat.setStatus(
                        SeatStatus.AVAILABLE
                );
            }

            booking.setStatus(
                    BookingStatus.CANCELLED
            );

            System.out.println(
                    "Booking cancelled"
            );

        } finally {

            for(Seat seat : seats) {
                seat.getLock().unlock();
            }
        }
    }
}


/* =========================================================
                            MAIN
   ========================================================= */

public class Main {

    public static void main(String[] args) {

        /*
            MOVIE
        */

        Movie movie =
                new Movie(
                        "M1",
                        "Interstellar",
                        180
                );

        /*
            SCREEN
        */

        Screen screen =
                new Screen(
                        "S1",
                        "Audi 1"
                );

        screen.addSeat(
                new Seat(
                        "A1",
                        SeatType.REGULAR
                )
        );

        screen.addSeat(
                new Seat(
                        "A2",
                        SeatType.PREMIUM
                )
        );

        screen.addSeat(
                new Seat(
                        "A3",
                        SeatType.RECLINER
                )
        );

        /*
            SHOW
        */

        Show show =
                new Show(
                        "SHOW1",
                        movie,
                        screen,
                        LocalDateTime.now()
                );

        /*
            USER
        */

        User user =
                new User(
                        "U1",
                        "Mineesha"
                );

        /*
            SERVICES
        */

        PricingStrategy pricingStrategy =
                new SimplePricingStrategy();

        PaymentService paymentService =
                new PaymentService();

        BookingService bookingService =
                new BookingService(
                        pricingStrategy,
                        paymentService
                );

        /*
            BOOK
        */

        Booking booking =
                bookingService.bookSeats(
                        user,
                        show,
                        Arrays.asList(
                                "A1",
                                "A2"
                        )
                );

        /*
            CANCEL
        */

        if(booking != null) {

            bookingService.cancelBooking(
                    booking.getBookingId()
            );
        }
    }
}
```
