import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;


/*
    ==========================================================
                        SPLITWISE SYSTEM
    ==========================================================

    FEATURES
    --------
    1. Create users
    2. Create groups
    3. Add expenses
    4. Equal split
    5. Exact split
    6. Percentage split
    7. Balance sheet
    8. Simplify debts
    9. Thread-safe balance updates
    10. Strategy Pattern
*/


/* ==========================================================
                        USER
   ========================================================== */

class User {

    private final String userId;

    private final String name;

    /*
        THREAD SAFETY
    */

    private final ReentrantLock lock =
            new ReentrantLock();

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

    public ReentrantLock getLock() {
        return lock;
    }

    @Override
    public String toString() {
        return name;
    }
}


/* ==========================================================
                        GROUP
   ========================================================== */

class Group {

    private final String groupId;

    private final String name;

    private final List<User> members =
            new ArrayList<>();

    public Group(
            String groupId,
            String name
    ) {

        this.groupId = groupId;
        this.name = name;
    }

    public void addMember(
            User user
    ) {
        members.add(user);
    }

    public List<User> getMembers() {
        return members;
    }

    public String getName() {
        return name;
    }
}


/* ==========================================================
                        SPLIT
   ========================================================== */

class Split {

    private final User user;

    private final double amount;

    public Split(
            User user,
            double amount
    ) {

        this.user = user;
        this.amount = amount;
    }

    public User getUser() {
        return user;
    }

    public double getAmount() {
        return amount;
    }
}


/* ==========================================================
                        EXPENSE
   ========================================================== */

class Expense {

    private final String expenseId;

    private final String description;

    private final double amount;

    private final User paidBy;

    private final List<Split> splits;

    public Expense(
            String expenseId,
            String description,
            double amount,
            User paidBy,
            List<Split> splits
    ) {

        this.expenseId = expenseId;

        this.description = description;

        this.amount = amount;

        this.paidBy = paidBy;

        this.splits = splits;
    }

    public User getPaidBy() {
        return paidBy;
    }

    public List<Split> getSplits() {
        return splits;
    }

    public double getAmount() {
        return amount;
    }
}


/* ==========================================================
                        SETTLEMENT
   ========================================================== */

class Settlement {

    private final User from;

    private final User to;

    private final double amount;

    public Settlement(
            User from,
            User to,
            double amount
    ) {

        this.from = from;
        this.to = to;
        this.amount = amount;
    }

    @Override
    public String toString() {

        return from.getName()
                + " pays "
                + to.getName()
                + " : "
                + amount;
    }
}


/* ==========================================================
                    SPLIT STRATEGY
   ========================================================== */

interface SplitStrategy {

    List<Split> calculateSplits(
            List<User> users,
            double amount,
            List<Double> values
    );
}


/* ==========================================================
                    EQUAL SPLIT
   ========================================================== */

class EqualSplitStrategy
        implements SplitStrategy {

    @Override
    public List<Split> calculateSplits(
            List<User> users,
            double amount,
            List<Double> values
    ) {

        List<Split> splits =
                new ArrayList<>();

        double splitAmount =
                amount / users.size();

        for(User user : users) {

            splits.add(
                    new Split(
                            user,
                            splitAmount
                    )
            );
        }

        return splits;
    }
}


/* ==========================================================
                    EXACT SPLIT
   ========================================================== */

class ExactSplitStrategy
        implements SplitStrategy {

    @Override
    public List<Split> calculateSplits(
            List<User> users,
            double amount,
            List<Double> values
    ) {

        List<Split> splits =
                new ArrayList<>();

        for(int i = 0;
                i < users.size();
                i++) {

            splits.add(
                    new Split(
                            users.get(i),
                            values.get(i)
                    )
            );
        }

        return splits;
    }
}


/* ==========================================================
                PERCENTAGE SPLIT
   ========================================================== */

class PercentageSplitStrategy
        implements SplitStrategy {

    @Override
    public List<Split> calculateSplits(
            List<User> users,
            double amount,
            List<Double> percentages
    ) {

        List<Split> splits =
                new ArrayList<>();

        for(int i = 0;
                i < users.size();
                i++) {

            double splitAmount =
                    amount
                            * percentages.get(i)
                            / 100;

            splits.add(
                    new Split(
                            users.get(i),
                            splitAmount
                    )
            );
        }

        return splits;
    }
}


/* ==========================================================
                    BALANCE SHEET
   ========================================================== */

class BalanceSheet {

    /*
        balanceMap[A][B] = amount A owes B
    */

    private final Map<String,
            Map<String, Double>>
            balanceMap =
            new ConcurrentHashMap<>();

    /*
        UPDATE BALANCE
    */

    public void updateBalance(
            User borrower,
            User lender,
            double amount
    ) {

        /*
            DEADLOCK PREVENTION
        */

        User first =
                borrower.getUserId()
                        .compareTo(
                                lender.getUserId()
                        ) < 0
                        ? borrower
                        : lender;

        User second =
                first == borrower
                        ? lender
                        : borrower;

        first.getLock().lock();
        second.getLock().lock();

        try {

            balanceMap.putIfAbsent(
                    borrower.getUserId(),
                    new ConcurrentHashMap<>()
            );

            Map<String, Double>
                    userBalances =
                    balanceMap.get(
                            borrower.getUserId()
                    );

            userBalances.put(

                    lender.getUserId(),

                    userBalances.getOrDefault(
                            lender.getUserId(),
                            0.0
                    ) + amount
            );

        } finally {

            second.getLock().unlock();
            first.getLock().unlock();
        }
    }

    /*
        RAW BALANCES
    */

    public void showBalances() {

        for(String borrower
                : balanceMap.keySet()) {

            Map<String, Double>
                    lenders =
                    balanceMap.get(borrower);

            for(String lender
                    : lenders.keySet()) {

                double amount =
                        lenders.get(lender);

                if(amount > 0) {

                    System.out.println(

                            borrower
                                    + " owes "
                                    + lender
                                    + " : "
                                    + amount
                    );
                }
            }
        }
    }

    /*
        NET BALANCES
    */

    public Map<String, Double>
    calculateNetBalances() {

        Map<String, Double> netBalances =
                new HashMap<>();

        for(String borrower
                : balanceMap.keySet()) {

            Map<String, Double>
                    lenders =
                    balanceMap.get(borrower);

            for(String lender
                    : lenders.keySet()) {

                double amount =
                        lenders.get(lender);

                /*
                    borrower pays
                */

                netBalances.put(

                        borrower,

                        netBalances.getOrDefault(
                                borrower,
                                0.0
                        ) - amount
                );

                /*
                    lender receives
                */

                netBalances.put(

                        lender,

                        netBalances.getOrDefault(
                                lender,
                                0.0
                        ) + amount
                );
            }
        }

        return netBalances;
    }
}


/* ==========================================================
                    DEBT SIMPLIFIER
   ========================================================== */

class DebtSimplifier {

    private final BalanceSheet
            balanceSheet;

    private final Map<String, User>
            users =
            new HashMap<>();

    public DebtSimplifier(
            BalanceSheet balanceSheet,
            List<User> userList
    ) {

        this.balanceSheet =
                balanceSheet;

        for(User user : userList) {

            users.put(
                    user.getUserId(),
                    user
            );
        }
    }

    public List<Settlement> simplifyDebts() {

        Map<String, Double>
                netBalances =
                balanceSheet
                        .calculateNetBalances();

        /*
            CREDITORS
        */

        PriorityQueue<Map.Entry<String, Double>>
                creditors =
                new PriorityQueue<>(

                        (a, b) -> Double.compare(
                                b.getValue(),
                                a.getValue()
                        )
                );

        /*
            DEBTORS
        */

        PriorityQueue<Map.Entry<String, Double>>
                debtors =
                new PriorityQueue<>(

                        Comparator.comparingDouble(
                                Map.Entry::getValue
                        )
                );

        /*
            SEPARATE USERS
        */

        for(Map.Entry<String, Double> entry
                : netBalances.entrySet()) {

            if(entry.getValue() > 0) {

                creditors.offer(entry);

            } else if(entry.getValue() < 0) {

                debtors.offer(entry);
            }
        }

        List<Settlement> settlements =
                new ArrayList<>();

        /*
            GREEDY SETTLEMENT
        */

        while(!creditors.isEmpty()
                &&
                !debtors.isEmpty()) {

            Map.Entry<String, Double>
                    creditor =
                    creditors.poll();

            Map.Entry<String, Double>
                    debtor =
                    debtors.poll();

            double settledAmount =
                    Math.min(

                            creditor.getValue(),

                            -debtor.getValue()
                    );

            settlements.add(

                    new Settlement(

                            users.get(
                                    debtor.getKey()
                            ),

                            users.get(
                                    creditor.getKey()
                            ),

                            settledAmount
                    )
            );

            double remainingCreditor =
                    creditor.getValue()
                            - settledAmount;

            double remainingDebtor =
                    debtor.getValue()
                            + settledAmount;

            if(remainingCreditor > 0) {

                creditors.offer(

                        new AbstractMap.SimpleEntry<>(

                                creditor.getKey(),

                                remainingCreditor
                        )
                );
            }

            if(remainingDebtor < 0) {

                debtors.offer(

                        new AbstractMap.SimpleEntry<>(

                                debtor.getKey(),

                                remainingDebtor
                        )
                );
            }
        }

        return settlements;
    }
}


/* ==========================================================
                    EXPENSE SERVICE
   ========================================================== */

class ExpenseService {

    private final BalanceSheet
            balanceSheet;

    public ExpenseService(
            BalanceSheet balanceSheet
    ) {

        this.balanceSheet =
                balanceSheet;
    }

    public void addExpense(
            String description,
            double amount,
            User paidBy,
            List<User> users,
            SplitStrategy strategy,
            List<Double> values
    ) {

        List<Split> splits =
                strategy.calculateSplits(
                        users,
                        amount,
                        values
                );

        Expense expense =
                new Expense(

                        UUID.randomUUID()
                                .toString(),

                        description,

                        amount,

                        paidBy,

                        splits
                );

        /*
            UPDATE BALANCES
        */

        for(Split split
                : expense.getSplits()) {

            User user =
                    split.getUser();

            if(user.getUserId()
                    .equals(
                            paidBy.getUserId()
                    )) {

                continue;
            }

            balanceSheet.updateBalance(

                    user,

                    paidBy,

                    split.getAmount()
            );
        }

        System.out.println(
                "Expense added: "
                        + description
        );
    }
}


/* ==========================================================
                            MAIN
   ========================================================== */

public class splitwise {

    public static void main(String[] args) {

        /*
            USERS
        */

        User u1 =
                new User(
                        "U1",
                        "Mineesha"
                );

        User u2 =
                new User(
                        "U2",
                        "Rahul"
                );

        User u3 =
                new User(
                        "U3",
                        "Aman"
                );

        /*
            GROUP
        */

        Group group =
                new Group(
                        "G1",
                        "Trip"
                );

        group.addMember(u1);
        group.addMember(u2);
        group.addMember(u3);

        /*
            SERVICES
        */

        BalanceSheet balanceSheet =
                new BalanceSheet();

        ExpenseService expenseService =
                new ExpenseService(
                        balanceSheet
                );

        /*
            EQUAL SPLIT
        */

        expenseService.addExpense(

                "Dinner",

                3000,

                u1,

                group.getMembers(),

                new EqualSplitStrategy(),

                null
        );

        /*
            EXACT SPLIT
        */

        expenseService.addExpense(

                "Taxi",

                1000,

                u2,

                Arrays.asList(
                        u1,
                        u2,
                        u3
                ),

                new ExactSplitStrategy(),

                Arrays.asList(
                        400.0,
                        300.0,
                        300.0
                )
        );

        /*
            PERCENTAGE SPLIT
        */

        expenseService.addExpense(

                "Hotel",

                5000,

                u3,

                Arrays.asList(
                        u1,
                        u2,
                        u3
                ),

                new PercentageSplitStrategy(),

                Arrays.asList(
                        50.0,
                        30.0,
                        20.0
                )
        );

        /*
            RAW BALANCES
        */

        System.out.println(
                "\nRaw Balances:"
        );

        balanceSheet.showBalances();

        /*
            SIMPLIFIED DEBTS
        */

        System.out.println(
                "\nSimplified Settlements:"
        );

        DebtSimplifier simplifier =
                new DebtSimplifier(

                        balanceSheet,

                        Arrays.asList(
                                u1,
                                u2,
                                u3
                        )
                );

        List<Settlement> settlements =
                simplifier.simplifyDebts();

        for(Settlement settlement
                : settlements) {

            System.out.println(
                    settlement
            );
        }
    }
}