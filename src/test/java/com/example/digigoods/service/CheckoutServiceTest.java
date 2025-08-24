package com.example.digigoods.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.digigoods.dto.CheckoutRequest;
import com.example.digigoods.dto.OrderResponse;
import com.example.digigoods.exception.ExcessiveDiscountException;
import com.example.digigoods.exception.UnauthorizedAccessException;
import com.example.digigoods.model.Discount;
import com.example.digigoods.model.DiscountType;
import com.example.digigoods.model.Order;
import com.example.digigoods.model.Product;
import com.example.digigoods.model.User;
import com.example.digigoods.repository.OrderRepository;
import com.example.digigoods.repository.UserRepository;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

  @Mock
  private ProductService productService;

  @Mock
  private DiscountService discountService;

  @Mock
  private OrderRepository orderRepository;

  @Mock
  private UserRepository userRepository;

  @InjectMocks
  private CheckoutService checkoutService;

  private CheckoutRequest checkoutRequest;
  private Product product1;
  private Product product2;
  private Discount generalDiscount;
  private Discount productSpecificDiscount;
  private User user;

  @BeforeEach
  void setUp() {
    checkoutRequest = new CheckoutRequest();
    checkoutRequest.setUserId(1L);
    checkoutRequest.setProductIds(List.of(1L, 2L));
    checkoutRequest.setDiscountCodes(List.of("GENERAL20"));

    product1 = new Product(1L, "Product 1", new BigDecimal("100.00"), 10);
    product2 = new Product(2L, "Product 2", new BigDecimal("50.00"), 5);

    generalDiscount = new Discount();
    generalDiscount.setCode("GENERAL20");
    generalDiscount.setPercentage(new BigDecimal("20.00"));
    generalDiscount.setType(DiscountType.GENERAL);

    productSpecificDiscount = new Discount();
    productSpecificDiscount.setCode("PRODUCT10");
    productSpecificDiscount.setPercentage(new BigDecimal("10.00"));
    productSpecificDiscount.setType(DiscountType.PRODUCT_SPECIFIC);
    productSpecificDiscount.setApplicableProducts(new HashSet<>(List.of(product1)));

    user = new User();
    user.setId(1L);
    user.setUsername("testuser");
  }

  @Test
  void processCheckout_validRequest_returnsSuccessResponse() {
    // Arrange
    List<Product> products = List.of(product1, product2);
    List<Discount> discounts = List.of(generalDiscount);

    when(productService.getProductsByIds(checkoutRequest.getProductIds()))
        .thenReturn(products);
    when(discountService.validateAndGetDiscounts(checkoutRequest.getDiscountCodes()))
        .thenReturn(discounts);
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));

    // Act
    OrderResponse response = checkoutService.processCheckout(checkoutRequest, 1L);

    // Assert
    assertEquals("Order created successfully!", response.getMessage());
    assertEquals(new BigDecimal("120.00"), response.getFinalPrice());
    verify(orderRepository).save(any(Order.class));
    verify(productService).validateAndUpdateStock(checkoutRequest.getProductIds());
    verify(discountService).updateDiscountUsage(discounts);
  }

  @Test
  void processCheckout_unauthorizedUser_throwsException() {
    // Arrange
    Long authenticatedUserId = 2L;

    // Act & Assert
    UnauthorizedAccessException exception = assertThrows(
        UnauthorizedAccessException.class,
        () -> checkoutService.processCheckout(checkoutRequest, authenticatedUserId));

    assertEquals("User cannot place order for another user", exception.getMessage());
    verify(productService, never()).getProductsByIds(anyList());
  }

  @Test
  void processCheckout_excessiveDiscount_throwsException() {
    // Arrange
    Discount excessiveDiscount = new Discount();
    excessiveDiscount.setPercentage(new BigDecimal("80.00"));
    excessiveDiscount.setType(DiscountType.GENERAL);

    List<Product> products = List.of(product1);
    List<Discount> discounts = List.of(excessiveDiscount);

    checkoutRequest.setProductIds(List.of(1L));

    when(productService.getProductsByIds(checkoutRequest.getProductIds()))
        .thenReturn(products);
    when(discountService.validateAndGetDiscounts(checkoutRequest.getDiscountCodes()))
        .thenReturn(discounts);

    // Act & Assert
    assertThrows(ExcessiveDiscountException.class,
        () -> checkoutService.processCheckout(checkoutRequest, 1L));

    verify(orderRepository, never()).save(any());
  }

  @Test
  void processCheckout_productSpecificDiscount_appliesCorrectly() {
    // Arrange
    List<Product> products = List.of(product1, product2);
    List<Discount> discounts = List.of(productSpecificDiscount);

    checkoutRequest.setDiscountCodes(List.of("PRODUCT10"));

    when(productService.getProductsByIds(checkoutRequest.getProductIds()))
        .thenReturn(products);
    when(discountService.validateAndGetDiscounts(checkoutRequest.getDiscountCodes()))
        .thenReturn(discounts);
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));

    // Act
    OrderResponse response = checkoutService.processCheckout(checkoutRequest, 1L);

    // Assert
    assertEquals(new BigDecimal("140.00"), response.getFinalPrice());
  }

  @Test
  void processCheckout_noDiscounts_returnsOriginalPrice() {
    // Arrange
    List<Product> products = List.of(product1, product2);
    List<Discount> discounts = List.of();

    checkoutRequest.setDiscountCodes(List.of());

    when(productService.getProductsByIds(checkoutRequest.getProductIds()))
        .thenReturn(products);
    when(discountService.validateAndGetDiscounts(checkoutRequest.getDiscountCodes()))
        .thenReturn(discounts);
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));

    // Act
    OrderResponse response = checkoutService.processCheckout(checkoutRequest, 1L);

    // Assert
    assertEquals(new BigDecimal("150.00"), response.getFinalPrice());
  }

  @Test
  void processCheckout_multipleGeneralDiscounts_appliesSequentially() {
    // Arrange
    Discount discount1 = new Discount();
    discount1.setPercentage(new BigDecimal("10.00"));
    discount1.setType(DiscountType.GENERAL);

    Discount discount2 = new Discount();
    discount2.setPercentage(new BigDecimal("20.00"));
    discount2.setType(DiscountType.GENERAL);

    List<Product> products = List.of(product1);
    List<Discount> discounts = List.of(discount1, discount2);

    checkoutRequest.setProductIds(List.of(1L));

    when(productService.getProductsByIds(checkoutRequest.getProductIds()))
        .thenReturn(products);
    when(discountService.validateAndGetDiscounts(checkoutRequest.getDiscountCodes()))
        .thenReturn(discounts);
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));

    // Act
    OrderResponse response = checkoutService.processCheckout(checkoutRequest, 1L);

    // Assert
    assertEquals(new BigDecimal("72.00"), response.getFinalPrice());
  }

  @Test
  void processCheckout_userNotFound_throwsException() {
    // Arrange
    // No mocking needed - exception thrown before any service calls

    // Act & Assert
    assertThrows(RuntimeException.class,
        () -> checkoutService.processCheckout(checkoutRequest, 1L));
  }

  @Test
  void processCheckout_duplicateProducts_calculatesCorrectly() {
    // Arrange
    checkoutRequest.setProductIds(List.of(1L, 1L, 2L));

    List<Product> products = List.of(product1, product2);
    List<Discount> discounts = List.of();

    when(productService.getProductsByIds(List.of(1L, 1L, 2L))).thenReturn(products);
    when(discountService.validateAndGetDiscounts(checkoutRequest.getDiscountCodes()))
        .thenReturn(discounts);
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));

    // Act
    OrderResponse response = checkoutService.processCheckout(checkoutRequest, 1L);

    // Assert
    assertEquals(new BigDecimal("250.00"), response.getFinalPrice());
  }
}
