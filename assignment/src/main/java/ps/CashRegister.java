package ps;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    private Map<Product, SalesRecord> salesCache = new LinkedHashMap<>();
    private Map<Product, SalesRecord> salesCacheP = new LinkedHashMap<>();
    private Product lastScanned = null;
    private LocalDate lastBBDate = null;
    private int lastSalesPrice = 0;
    private List<SalesRecord> list = new ArrayList<>();
    private List<SalesRecord> list2 = new ArrayList<>();
    
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
    public void scan(int barcode)  {
/*        if (this.lastScanned != null) {
            finalizeSalesTransaction();
        }*/
        
        try {
            if(this.salesService.lookupProduct(barcode) == null){
                this.ui.displayErrorMessage("No product found!");
            }

        if(this.salesCache.containsKey(this.salesService.lookupProduct(barcode))) {
            this.salesCache.get(this.salesService.lookupProduct(barcode)).increaseQuantity(1);
        } else if(this.salesCacheP.containsKey(this.salesService.lookupProduct(barcode))){
            this.salesCacheP.get(this.salesService.lookupProduct(barcode)).increaseQuantity(1);
        } else{
            SalesRecord sale = new SalesRecord(barcode, LocalDate.now(this.clock), this.salesService.lookupProduct(barcode).getPrice());
            if(this.salesService.lookupProduct(barcode).isPerishable()){
                this.salesCacheP.put(this.salesService.lookupProduct(barcode), sale);
            } else {
                this.salesCache.put(this.salesService.lookupProduct(barcode), sale);
            }

        }

        this.lastScanned = this.salesService.lookupProduct(barcode);
        this.ui.displayProduct(this.lastScanned);

        if (this.lastScanned.isPerishable()) {
            this.ui.displayCalendar();
        } else {
            this.lastSalesPrice = this.lastScanned.getPrice();
            this.lastBBDate = LocalDate.MAX;
        }
        } catch (UnknownProductException e) {
            e.printStackTrace();
        }
        
    }

    /**
     * Submit the sales to the sales service, finalizing the sales transaction.
     * All salesRecords in the salesCache are stored (one-by-one) in the salesService.
     * All caches are reset.
     */
    public void finalizeSalesTransaction() {


/*        if (this.lastScanned.isPerishable()) {
            this.lastSalesPrice = this.lastScanned.getPrice();
            correctSalesPrice(this.lastBBDate);
        } else {*/
            //SalesRecord sale = new SalesRecord(this.lastScanned.getBarcode(), LocalDate.now(this.clock), this.lastSalesPrice);
        /*if (this.lastScanned != null && this.lastScanned.isPerishable()) {
            this.lastSalesPrice = this.lastScanned.getPrice();
            correctSalesPrice(this.lastBBDate);
        }*/
        for (Map.Entry<Product, SalesRecord> sales : this.salesCache.entrySet()) {
            /*if(sales.getKey().isPerishable()){
                list.add(sales.getValue());
            } else{
                list2.add(sales.getValue());
            }*/
            this.salesService.sold(sales.getValue());
        }
        for (Map.Entry<Product, SalesRecord> sales : this.salesCacheP.entrySet()) {
            this.salesService.sold(sales.getValue());
        }

        this.salesCache.clear();
        this.salesCacheP.clear();
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
            }
            this.salesCacheP.get(this.lastScanned).setSalesPrice(salesPrice);
        }

        this.lastBBDate = null;
        this.lastSalesPrice = 0;
        //this.lastScanned = null;
        //finalizeSalesTransaction();
        
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

            for (Map.Entry<Product, SalesRecord> sales : this.salesCacheP.entrySet()) {
                this.printer.println("Product: " + sales.getKey().getDescription() + ", Sales price: " + sales.getValue().getSalesPrice() + ", Quantity: " + sales.getValue().getQuantity());
            }
            for (Map.Entry<Product, SalesRecord> sales : this.salesCache.entrySet()) {
                this.printer.println("Product: " + sales.getKey().getDescription() + ", Sales price: " + sales.getValue().getSalesPrice() + ", Quantity: " + sales.getValue().getQuantity());

        }

        //this.salesCache.clear();
        /*int j;
        for(j = 0; j < list.size(); j++){
            this.printer.println("Product: " + salesService.lookupProduct(list.get(j).getBarcode()).getDescription() + ", Sales price: " + list.get(j).getSalesPrice() + ", Quantity: " + list.get(j).getQuantity());
        }
        for(j = 0; j < list2.size(); j++){
            this.printer.println("Product: " + salesService.lookupProduct(list2.get(j).getBarcode()).getDescription() + ", Sales price: " + list2.get(j).getSalesPrice() + ", Quantity: " + list2.get(j).getQuantity());
        }
        this.list.clear();;
        this.list2.clear();*/
    }
}
