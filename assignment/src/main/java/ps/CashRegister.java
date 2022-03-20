package ps;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Class to be developed test driven with Mockito.
 *
 * @author Pieter van den Hombergh / Richard van den Ham
 */
class CashRegister {

    private final Clock clock;
    private final Printer printer;
    private final UI ui;
    private final SalesService salesService;

    private Map<Product, SalesRecord> perishable = new LinkedHashMap<>();
    private Map<Product, SalesRecord> nonPerishable = new LinkedHashMap<>();
    private Product lastScanned = null;
    private LocalDate lastBBDate = null;
    private int lastSalesPrice = 0;
    
    // Declare a field to keep a salesCache, which is a mapping between a Product and a SalesRecord.
    // When a product gets scanned multiple times, the quantity of the salesRecord is increased. 
    // A LinkedHashMap has the benefit that, in contrast to the HashMap, the order in which 
    // the items were added is preserved.
    
    // Declare a field to keep track of the last scanned product, initially being null.

    
    /**
     * Create a business object
     *
     * @param clock wall clock
     * @param printer to use
     * @param ui to use
     * @param salesService to use
     */
    CashRegister(Clock clock, Printer printer, UI ui, SalesService salesService) {
        this.clock = clock;
        this.printer = printer;
        this.ui = ui;
        this.salesService = salesService;
    }

    /**
     * The scan method is triggered by scanning a product by the cashier.
     * Get the product from the salesService. If the product can't be found, an UnknownProductException is thrown and the
     * error message from the exception is shown on the display (ui).
     * If found, check if there is a salesRecord for this product already. If not, create one. If it exists, update the quantity.
     * In case a perishable product was scanned, the cashier should get a calendar on his/her display.
     * The product is displayed on the display.
     * @param barcode 
     */
    public void scan(int barcode) throws UnknownProductException, UnknownBestBeforeException { //added exception handling
        if (this.lastScanned != null) {
            finalizeSalesTransaction();
        }

        if(this.salesService.lookupProduct(barcode) == null){
            ui.displayErrorMessage("No product found!");
            throw new UnknownProductException("No product found!");
        }

        this.lastScanned = this.salesService.lookupProduct(barcode);
        this.ui.displayProduct(this.lastScanned);

        if (this.lastScanned.isPerishable()) {
            this.ui.displayCalendar();
        } else {
            this.lastSalesPrice = this.lastScanned.getPrice();
            this.lastBBDate = LocalDate.MAX;
        }
        
    }

    /**
     * Submit the sales to the sales service, finalizing the sales transaction.
     * All salesRecords in the salesCache are stored (one-by-one) in the salesService.
     * All caches are reset.
     */
    public void finalizeSalesTransaction() throws UnknownBestBeforeException { //added exception handling


        if (this.lastScanned.isPerishable()) {
            this.lastSalesPrice = this.lastScanned.getPrice();
            correctSalesPrice(this.lastBBDate);
        } else {
            SalesRecord sale = new SalesRecord(this.lastScanned.getBarcode(), LocalDate.now(this.clock), this.lastSalesPrice);
            this.salesService.sold(sale);

            this.nonPerishable.put(this.lastScanned, sale);
        }

        this.lastBBDate = null;
        this.lastSalesPrice = 0;
        this.lastScanned = null;
        
    }

    /**
     * Correct the sales price of the last scanned product by considering the
     * given best before date, then submit the product to the service and save
     * in list.
     *
     * This method consults the clock to see if the product is eligible for a
     * price reduction because it is near or at its best before date.
     * 
     * Precondition is that the last scanned product is the perishable product. 
     * You don't need to check that in your code. 
     * 
     * To find the number of days from now till the bestBeforeDate, use
     * LocalDate.now(clock).until(bestBeforeDate).getDays();
     * 
     * Depending on the number of days, update the price in the salesRecord folowing the 
     * pricing strategy as described in the assignment
     *
     * Update the salesRecord belonging to the last scanned product if necessary, so 
     * update the price and set the BestBeforeDate.
     * 
     * @param bestBeforeDate
     * @throws UnknownBestBeforeException in case the best before date is null.
     */
    public void correctSalesPrice(LocalDate bestBeforeDate) throws UnknownBestBeforeException {
        if(bestBeforeDate == null){
            throw new UnknownBestBeforeException("Best before date must not be null!");
        }

        int salesPrice = 0;

        if (this.lastScanned != null) {
            if (LocalDate.now(this.clock).until(bestBeforeDate).getDays() >= 2) {
                salesPrice = this.lastScanned.getPrice();
            } else if (LocalDate.now(this.clock).until(bestBeforeDate).getDays() == 1) {
                salesPrice = (int) ((double) this.lastScanned.getPrice() * 0.65);
            } else if (LocalDate.now(this.clock).until(bestBeforeDate).getDays() == 0) {
                salesPrice = (int) ((double) this.lastScanned.getPrice() * 0.35);
            } else {
                salesPrice = 0;
            }

            SalesRecord sale = new SalesRecord(this.lastScanned.getBarcode(), LocalDate.now(this.clock), salesPrice);
            this.salesService.sold(sale);
            this.perishable.put(this.lastScanned, sale);
        }

        this.lastBBDate = null;
        this.lastSalesPrice = 0;
        this.lastScanned = null;
        
    }

    /**
     * Print the receipt for all the sold products, to hand the receipt to the
     * customer. The receipt contains lines containing: the product description,
     * the (possibly reduced) sales price per piece and the quantity, separated by
     * a tab.
     * The order of printing is the order of scanning, however Perishable
     * products are printed first. The non-perishables afterwards.
     */
    public void printReceipt() {
        for (Map.Entry<Product, SalesRecord> perishables : this.perishable.entrySet()) {
            this.printer.println("Product: " + perishables.getKey().getDescription() + ", Sales price: " + perishables.getValue().getSalesPrice() + ", Quantity: " + perishables.getValue().getQuantity());
        }

        for (Map.Entry<Product, SalesRecord> nonPerishables : this.nonPerishable.entrySet()) {
            this.printer.println("Product: " + nonPerishables.getKey().getDescription() + ", Sales price: " + nonPerishables.getValue().getSalesPrice() + ", Quantity: " + nonPerishables.getValue().getQuantity());
        }

        this.perishable.clear();
        this.nonPerishable.clear();
    }
}
