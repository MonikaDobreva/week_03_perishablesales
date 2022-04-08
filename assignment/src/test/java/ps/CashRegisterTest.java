package ps;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

import net.bytebuddy.asm.Advice;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.junit.jupiter.MockitoExtension;

/**
 * CashRegister is the business class to test. It gets bar code scanner input,
 * is able to output to the ui, and uses the SalesService.
 *
 * @author Pieter van den Hombergh / Richard van den Ham
 */
@ExtendWith(MockitoExtension.class)
public class CashRegisterTest {

    Product lamp = new Product("led lamp", "Led Lamp", 250, 1_234, false);
    Product banana = new Product("banana", "Bananas Fyffes", 150, 9_234, true);
    Product cheese = new Product("cheese", "Gouda 48+", 800, 7_687, true);
    Clock clock = Clock.systemDefaultZone();

    Map<String, Product> products = Map.of(
            "lamp", lamp,
            "banana", banana,
            "cheese", cheese
    );

    @Mock
    Printer printer;

    @Mock
    SalesService salesService;

    @Mock
    UI ui;

    @Captor
    private ArgumentCaptor<SalesRecord> salesRecordCaptor;

    @Captor
    private ArgumentCaptor<Product> productCaptor;

    @Captor
    private ArgumentCaptor<String> stringLineCaptor;

    CashRegister cashRegister;

    @BeforeEach
    void setup() {
        cashRegister = new CashRegister(clock, printer, ui, salesService);
    }

    /**
     * Test that after a scan, a non perishable product is looked up and
     * correctly displayed.Have a look at requirements in the JavaDoc of the
     * CashRegister methods. Test product is non-perishable, e.g. led lamp.
     * <ul>
     * <li>Train the mocked salesService and check if a lookup has been
     * done.<li>Check if the mocked UI was asked to display the
     * product.<li>Ensure that ui.displayCalendar is not called.<b>NOTE
     *
     * @throws ps.UnknownProductException
     */
    @Test
    public void lookupandDisplayNonPerishableProduct() throws UnknownProductException {
        when(salesService.lookupProduct(lamp.getBarcode())).thenReturn(lamp);
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);

        assertThatCode(() -> {
            cashRegister.scan(lamp.getBarcode());
        })
                .doesNotThrowAnyException();

        //verify(salesService).lookupProduct(lamp.getBarcode());

        verify(ui, times(1)).displayProduct(productCaptor.capture());

        List<Product> displayedProducts = productCaptor.getAllValues();
        assertThat(displayedProducts)
                .contains(lamp);

        verify(ui, never())
                .displayCalendar();
        //fail( "method lookupandDisplayNonPerishableProduct reached end. You know what to do." );
    }

    /**
     * Test that both the product and calendar are displayed when a perishable
     * product is scanned.
     *
     * @throws UnknownProductException but don't worry about it, since you test
     *                                 with an existing product now.
     */
    @Test
    public void lookupandDisplayPerishableProduct() throws UnknownProductException, UnknownBestBeforeException {
        when(salesService.lookupProduct(banana.getBarcode())).thenReturn(banana);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);

        cashRegister.scan(banana.getBarcode());

        // verify(salesService).lookupProduct(banana.getBarcode());

        verify(ui, times(1)).displayProduct(productCaptor.capture());

        List<Product> displayedProducts = productCaptor.getAllValues();
        assertThat(displayedProducts).contains(banana);

        //verify(ui).displayCalendar();
        //fail( "method lookupandDisplayPerishableProduct reached end. You know what to do." );
    }

    /**
     * Scan a product, finalize the sales transaction, then verify that the
     * correct salesRecord is sent to the SalesService. Use a non-perishable
     * product. SalesRecord has no equals method (and do not add it), instead
     * use {@code assertThat(...).usingRecursiveComparison().isEqualTo(...)}.
     * Also verify that if you print a receipt after finalizing, there is no output.
     *
     * @throws ps.UnknownProductException
     */
    @Test
    public void finalizeSalesTransaction() throws UnknownProductException, UnknownBestBeforeException {
        when(salesService.lookupProduct(lamp.getBarcode())).thenReturn(lamp);

        SalesRecord sale = new SalesRecord(lamp.getBarcode(), LocalDate.now(clock), lamp.getPrice());

        cashRegister.scan(lamp.getBarcode());
        cashRegister.finalizeSalesTransaction();


        ArgumentCaptor<SalesRecord> saleCaptor = ArgumentCaptor.forClass(SalesRecord.class);

        ArgumentCaptor<String> printc = ArgumentCaptor.forClass(String.class);


        verify(salesService).sold(saleCaptor.capture());

        cashRegister.printReceipt();
        //call finalize, then print

        verify(printer, never()).println(printc.capture());

        /*assertThat(saleCaptor.getValue())
                .usingRecursiveComparison().isEqualTo(sale);*/

        SoftAssertions.assertSoftly(softly -> {
                   /*softly.assertThat(printc.getValue())
                           .isEqualTo(" ");*/
            softly.assertThat(saleCaptor.getValue())
                    .usingRecursiveComparison().isEqualTo(sale);
            softly.assertThat(printc.getAllValues().isEmpty()).isTrue();
        });
        //fail( "method finalizeSalesTransaction reached end. You know what to do." );
    }

    /**
     * Verify price reductions. For a perishable product with: 10 days till
     * best-before, no reduction; 2 days till best-before, no reduction; 1 day
     * till best-before, 35% price reduction; 0 days till best-before (so sales
     * date is best-before date), 65% price reduction; -1 days till best-before
     * (product over date), 100% price reduction.
     * <p>
     * Check the correct price using the salesService and an argument captor.
     */
    @ParameterizedTest
    @CsvSource({
            "banana,10,100",
            "banana,2,100",
            "banana,1,65",
            "banana,0,35",
            "banana,-1,0",})
    public void priceReductionNearBestBefore(String productName, int daysBest, int pricePercent) throws UnknownBestBeforeException, UnknownProductException {
        SalesRecord sale = new SalesRecord(products.get(productName).getBarcode(), LocalDate.now(clock), products.get(productName).getPrice());
        when(salesService.lookupProduct(products.get(productName).getBarcode())).thenReturn(products.get(productName));

        ArgumentCaptor<SalesRecord> saleCaptor = ArgumentCaptor.forClass(SalesRecord.class);

        cashRegister.scan(products.get(productName).getBarcode());

        cashRegister.correctSalesPrice(LocalDate.now(clock).plusDays(daysBest));

        cashRegister.finalizeSalesTransaction();
        verify(salesService).sold(saleCaptor.capture());

        int expected = (sale.getSalesPrice() * pricePercent) / 100;
        assertThat(saleCaptor.getValue().getSalesPrice())
                .isEqualTo(expected);


        //fail( "method priceReductionNearBestBefore reached end. You know what to do." );
    }

    /**
     * When multiple products are scanned, the resulting lines on the receipt
     * should be perishable first, not perishables last. Scan a banana, led lamp
     * and a cheese. The products should appear on the printed receipt in
     * banana, cheese, lamp order. The printed product line on the receipt
     * should contain description, (reduced) salesprice per piece and the
     * quantity.
     */
    @Test
    public void printInProperOrder() throws UnknownBestBeforeException, UnknownProductException {
        ArgumentCaptor<String> lineCaptor = ArgumentCaptor.forClass(String.class);

        SalesRecord sale = new SalesRecord(banana.getBarcode(), LocalDate.now(clock), banana.getPrice());
        when(salesService.lookupProduct(banana.getBarcode())).thenReturn(banana);

        SalesRecord sale2 = new SalesRecord(lamp.getBarcode(), LocalDate.now(clock), lamp.getPrice());
        when(salesService.lookupProduct(lamp.getBarcode())).thenReturn(lamp);

        SalesRecord sale3 = new SalesRecord(cheese.getBarcode(), LocalDate.now(clock), cheese.getPrice());
        when(salesService.lookupProduct(cheese.getBarcode())).thenReturn(cheese);

        cashRegister.scan(banana.getBarcode());
        cashRegister.correctSalesPrice(LocalDate.now(clock).plusDays(0));
        cashRegister.scan(lamp.getBarcode());
        cashRegister.scan(cheese.getBarcode());
        cashRegister.correctSalesPrice(LocalDate.now(clock).plusDays(1));

        cashRegister.printReceipt();

        verify(printer, times(3)).println(lineCaptor.capture());

        int sale1 = (int) (sale.getSalesPrice() * 0.35);
        int sale33 = (int) (sale3.getSalesPrice() * 0.65);

        SoftAssertions.assertSoftly(softly -> {
            List<String> printedProducts = lineCaptor.getAllValues();
            softly.assertThat(printedProducts.get(0))
                    .isEqualTo("Product: " + banana.getDescription() + ", Sales price: " + sale1 + ", Quantity: " + sale.getQuantity());
            softly.assertThat(printedProducts.get(1))
                    .isEqualTo("Product: " + cheese.getDescription() + ", Sales price: " + sale33 + ", Quantity: " + sale3.getQuantity());
            softly.assertThat(printedProducts.get(2))
                    .isEqualTo("Product: " + lamp.getDescription() + ", Sales price: " + sale2.getSalesPrice() + ", Quantity: " + sale2.getQuantity());
        });
        //fail( "method printInProperOrder reached end. You know what to do." );


    }

    /**
     * Test that invoking correctSalesPrice with null parameter results in
     * exception.
     *
     * @throws UnknownProductException (but that one is irrelevant). First scan
     *                                 (scan) a perishable product. Afterwards invoke correctSalesPrice with
     *                                 null parameter. An UnknownProductException should be thrown.
     */
    @Test
    public void correctSalesPriceWithBestBeforeIsNullThrowsException() throws UnknownProductException, UnknownBestBeforeException {
        when(salesService.lookupProduct(banana.getBarcode())).thenReturn(banana);
        cashRegister.scan(banana.getBarcode());

        ThrowableAssert.ThrowingCallable code = () -> {
            cashRegister.correctSalesPrice(null);
        };

        assertThatThrownBy(code)
                .isInstanceOf(Exception.class)
                .isExactlyInstanceOf(UnknownBestBeforeException.class)
                .hasMessageContaining("Best before date must not be null!");
        //fail( "method correctSalesPriceWithBestBeforeIsNull reached end. You know what to do." );
    }

    /**
     * Test scanning an unknown product results in error message on GUI.
     */
    @Test
    public void lookupUnknownProductShouldDisplayErrorMessage() throws UnknownProductException, UnknownBestBeforeException {
        //when(salesService.lookupProduct(5353)).thenThrow(new UnknownProductException("No product found!"));
        //cashRegister.scan(5353);


        ThrowableAssert.ThrowingCallable code = () -> {
            cashRegister.scan(5353);
        };

        assertThatThrownBy(code)
                .isInstanceOf(Exception.class)
                .isExactlyInstanceOf(UnknownProductException.class)
                .hasMessageContaining("No product found!");
        //fail( "method lookupUnknownProduct... reached end. You know what to do." );
    }

    /**
     * Test that a product that is scanned twice, is registered in the
     * salesService with the proper quantity AND make sure printer prints the
     * proper quantity as well.
     *
     * @throws UnknownProductException
     */
    @Test
    public void scanProductTwiceShouldIncreaseQuantity() throws UnknownProductException, UnknownBestBeforeException {
        /*SalesRecord sale = new SalesRecord(lamp.getBarcode(), LocalDate.now(clock), lamp.getPrice());


        assertThat(sale.getQuantity())
                .isEqualTo(1);*/
        SalesRecord sale = new SalesRecord(lamp.getBarcode(), LocalDate.now(clock), lamp.getPrice());
        sale.increaseQuantity(1);
        when(salesService.lookupProduct(lamp.getBarcode())).thenReturn(lamp);


        ArgumentCaptor<String> lineCaptor = ArgumentCaptor.forClass(String.class);

        cashRegister.scan(lamp.getBarcode());
        cashRegister.scan(lamp.getBarcode());
        cashRegister.printReceipt();

        verify(printer).println(lineCaptor.capture());

        SoftAssertions.assertSoftly(softly -> {
            List<String> printedProducts = lineCaptor.getAllValues();
            softly.assertThat(printedProducts.get(0))
                    .isEqualTo("Product: " + lamp.getDescription() + ", Sales price: " + lamp.getPrice() + ", Quantity: " + 2);
            softly.assertThat(sale.getQuantity())
                    .isEqualTo(2);
        });


        //fail( "method scanProductTwice reached end. You know what to do." );
    }

/*    @Test
    public void fixIt() throws UnknownProductException, UnknownBestBeforeException {
        when(salesService.lookupProduct(banana.getBarcode())).thenReturn(banana);
        cashRegister.scan(banana.getBarcode());
        cashRegister.finalizeSalesTransaction();

        assertThat()
    }*/
}
