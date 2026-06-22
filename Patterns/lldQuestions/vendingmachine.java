import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;


/*
    ==========================================================
                    VENDING MACHINE SYSTEM
    ==========================================================

    FEATURES
    --------
    1. Add products
    2. Select product
    3. Insert money
    4. Dispense product
    5. Return change
    6. Inventory management
    7. Thread-safe dispensing
    8. State Pattern
    9. Extensible payment model
*/


/* ==========================================================
                        ENUMS
   ========================================================== */

enum ProductType {
    COKE,
    PEPSI,
    CHIPS,
    CHOCOLATE
}

enum Coin {
    ONE(1),
    TWO(2),
    FIVE(5),
    TEN(10);

    private final int value;

    Coin(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}


/* ==========================================================
                        PRODUCT
   ========================================================== */

class Product {

    private final String productId;

    private final ProductType type;

    private final int price;

    public Product(
            String productId,
            ProductType type,
            int price
    ) {
        this.productId = productId;
        this.type = type;
        this.price = price;
    }

    public String getProductId() {
        return productId;
    }

    public ProductType getType() {
        return type;
    }

    public int getPrice() {
        return price;
    }
}


/* ==========================================================
                        INVENTORY ITEM
   ========================================================== */

class InventoryItem {

    private final Product product;

    private int quantity;

    /*
        THREAD SAFETY

        Lock per product.
    */

    private final ReentrantLock lock = new ReentrantLock();

    public InventoryItem(
            Product product,
            int quantity
    ) {
        this.product = product;
        this.quantity = quantity;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public void incrementQuantity(
            int qty
    ) {
        quantity += qty;
    }

    public void decrementQuantity() {
        quantity--;
    }

    public ReentrantLock getLock() {
        return lock;
    }
}


/* ==========================================================
                    INVENTORY SERVICE
   ========================================================== */

class InventoryService {

    private final Map<ProductType, InventoryItem>
            inventoryMap =
            new ConcurrentHashMap<>();

    public void addProduct(
            Product product,
            int quantity
    ) {

        inventoryMap.put(
                product.getType(),
                new InventoryItem(
                        product,
                        quantity
                )
        );
    }

    public InventoryItem getInventoryItem(
            ProductType type
    ) {

        return inventoryMap.get(type);
    }

    public void displayProducts() {

        for(InventoryItem item
                : inventoryMap.values()) {

            System.out.println(
                    item.getProduct().getType()
                            + " | Price: "
                            + item.getProduct().getPrice()
                            + " | Quantity: "
                            + item.getQuantity()
            );
        }
    }
}


/* ==========================================================
                    VENDING MACHINE
   ========================================================== */

class VendingMachine {

    private final InventoryService
            inventoryService;

    private VendingMachineState currentState;

    private ProductType selectedProduct;

    private int insertedAmount;

    public VendingMachine(
            InventoryService inventoryService
    ) {

        this.inventoryService =
                inventoryService;

        this.currentState =
                new IdleState(this);
    }


    public void setCurrentState(
            VendingMachineState state
    ) {
        this.currentState = state;
    }


    public void setSelectedProduct(
            ProductType selectedProduct
    ) {
        this.selectedProduct =
                selectedProduct;
    }

    public int getInsertedAmount() {
        return insertedAmount;
    }

    public void addAmount(
            int amount
    ) {
        insertedAmount += amount;
    }

    public void resetMachine() {

        selectedProduct = null;

        insertedAmount = 0;

        currentState =
                new IdleState(this);
    }

    /*
        APIs
    */

    public void selectProduct(
            ProductType type
    ) {
        currentState.selectProduct(type);
    }

    public void insertCoin(
            Coin coin
    ) {
        currentState.insertCoin(coin);
    }

    public void dispenseProduct() {
        currentState.dispenseProduct();
    }

    public void cancel() {
        currentState.cancel();
    }
}



/* ==========================================================
                    MACHINE STATE
   ========================================================== */

interface VendingMachineState {

    void selectProduct(
            ProductType type
    );

    void insertCoin(
            Coin coin
    );

    void dispenseProduct();

    void cancel();
}

/* ==========================================================
                        IDLE STATE
   ========================================================== */

class IdleState
        implements VendingMachineState {

    private final VendingMachine machine;

    public IdleState(
            VendingMachine machine
    ) {
        this.machine = machine;
    }

    @Override
    public void selectProduct(
            ProductType type
    ) {

        InventoryItem item =
                machine
                        .getInventoryService()
                        .getInventoryItem(type);

        if(item == null
                || item.getQuantity() <= 0) {

            System.out.println(
                    "Product unavailable"
            );

            return;
        }

        machine.setSelectedProduct(type);

        machine.setCurrentState(
                new ReadyState(machine)
        );

        System.out.println(
                "Selected product: " + type
        );
    }

    @Override
    public void insertCoin(
            Coin coin
    ) {
        System.out.println(
                "Select product first"
        );
    }

    @Override
    public void dispenseProduct() {
        System.out.println(
                "Select product first"
        );
    }

    @Override
    public void cancel() {
        System.out.println(
                "Nothing to cancel"
        );
    }
}


/* ==========================================================
                        READY STATE
   ========================================================== */

class ReadyState
        implements VendingMachineState {

    private final VendingMachine machine;

    public ReadyState(
            VendingMachine machine
    ) {
        this.machine = machine;
    }

    @Override
    public void selectProduct(
            ProductType type
    ) {
        System.out.println(
                "Product already selected"
        );
    }

    @Override
    public void insertCoin(
            Coin coin
    ) {

        machine.addAmount(
                coin.getValue()
        );

        System.out.println(
                "Inserted: "
                        + coin.getValue()
        );

        InventoryItem item =
                machine
                        .getInventoryService()
                        .getInventoryItem(
                                machine.getSelectedProduct()
                        );

        int price =
                item.getProduct().getPrice();

        if(machine.getInsertedAmount()
                >= price) {

            machine.setCurrentState(
                    new DispenseState(machine)
            );
        }
    }

    @Override
    public void dispenseProduct() {

        System.out.println(
                "Insufficient money"
        );
    }

    @Override
    public void cancel() {

        System.out.println(
                "Returning amount: "
                        + machine.getInsertedAmount()
        );

        machine.resetMachine();
    }
}


/* ==========================================================
                    DISPENSE STATE
   ========================================================== */

class DispenseState
        implements VendingMachineState {

    private final VendingMachine machine;

    public DispenseState(
            VendingMachine machine
    ) {
        this.machine = machine;
    }

    @Override
    public void selectProduct(
            ProductType type
    ) {
        System.out.println(
                "Dispensing in progress"
        );
    }

    @Override
    public void insertCoin(
            Coin coin
    ) {
        System.out.println(
                "Already enough money"
        );
    }

    @Override
    public void dispenseProduct() {

        InventoryItem item =
                machine
                        .getInventoryService()
                        .getInventoryItem(
                                machine.getSelectedProduct()
                        );

        /*
            THREAD SAFETY

            Prevent double dispense.
        */

        ReentrantLock lock =
                item.getLock();

        lock.lock();

        try {

            if(item.getQuantity() <= 0) {

                System.out.println(
                        "Out of stock"
                );

                machine.resetMachine();

                return;
            }

            item.decrementQuantity();

            int price =
                    item.getProduct().getPrice();

            int change =
                    machine.getInsertedAmount()
                            - price;

            System.out.println(
                    "Dispensing: "
                            + item.getProduct().getType()
            );

            System.out.println(
                    "Returning change: "
                            + change
            );

            machine.resetMachine();

        } finally {
            lock.unlock();
        }
    }

    @Override
    public void cancel() {

        System.out.println(
                "Cannot cancel now"
        );
    }
}


/* ==========================================================
                            MAIN
   ========================================================== */

public class Main {

    public static void main(String[] args) {

        InventoryService inventoryService =
                new InventoryService();

        /*
            PRODUCTS
        */

        inventoryService.addProduct(

                new Product(
                        "P1",
                        ProductType.COKE,
                        15
                ),

                5
        );

        inventoryService.addProduct(

                new Product(
                        "P2",
                        ProductType.CHIPS,
                        10
                ),

                3
        );

        inventoryService.displayProducts();

        /*
            MACHINE
        */

        VendingMachine machine =
                new VendingMachine(
                        inventoryService
                );

        /*
            FLOW
        */

        machine.selectProduct(
                ProductType.COKE
        );

        machine.insertCoin(Coin.TEN);

        machine.insertCoin(Coin.FIVE);

        machine.dispenseProduct();
    }
}
```
