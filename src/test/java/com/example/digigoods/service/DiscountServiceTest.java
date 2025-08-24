package com.example.digigoods.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.digigoods.exception.InvalidDiscountException;
import com.example.digigoods.model.Discount;
import com.example.digigoods.model.DiscountType;
import com.example.digigoods.repository.DiscountRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiscountServiceTest {

  @Mock
  private DiscountRepository discountRepository;

  @InjectMocks
  private DiscountService discountService;

  private Discount validDiscount;
  private Discount expiredDiscount;
  private Discount futureDiscount;
  private Discount noUsesDiscount;

  @BeforeEach
  void setUp() {
    validDiscount = new Discount();
    validDiscount.setCode("VALID20");
    validDiscount.setPercentage(new BigDecimal("20.00"));
    validDiscount.setType(DiscountType.GENERAL);
    validDiscount.setValidFrom(LocalDate.now().minusDays(1));
    validDiscount.setValidUntil(LocalDate.now().plusDays(30));
    validDiscount.setRemainingUses(5);
    validDiscount.setApplicableProducts(new HashSet<>());

    expiredDiscount = new Discount();
    expiredDiscount.setCode("EXPIRED20");
    expiredDiscount.setPercentage(new BigDecimal("20.00"));
    expiredDiscount.setType(DiscountType.GENERAL);
    expiredDiscount.setValidFrom(LocalDate.now().minusDays(30));
    expiredDiscount.setValidUntil(LocalDate.now().minusDays(1));
    expiredDiscount.setRemainingUses(5);
    expiredDiscount.setApplicableProducts(new HashSet<>());

    futureDiscount = new Discount();
    futureDiscount.setCode("FUTURE20");
    futureDiscount.setPercentage(new BigDecimal("20.00"));
    futureDiscount.setType(DiscountType.GENERAL);
    futureDiscount.setValidFrom(LocalDate.now().plusDays(1));
    futureDiscount.setValidUntil(LocalDate.now().plusDays(30));
    futureDiscount.setRemainingUses(5);
    futureDiscount.setApplicableProducts(new HashSet<>());

    noUsesDiscount = new Discount();
    noUsesDiscount.setCode("NOUSES20");
    noUsesDiscount.setPercentage(new BigDecimal("20.00"));
    noUsesDiscount.setType(DiscountType.GENERAL);
    noUsesDiscount.setValidFrom(LocalDate.now().minusDays(1));
    noUsesDiscount.setValidUntil(LocalDate.now().plusDays(30));
    noUsesDiscount.setRemainingUses(0);
    noUsesDiscount.setApplicableProducts(new HashSet<>());
  }

  @Test
  @DisplayName("Given valid discount codes, when getting discounts by codes, then return discounts")
  void givenValidDiscountCodes_whenGettingDiscountsByCodes_thenReturnDiscounts() {
    // Arrange
    List<String> discountCodes = List.of("VALID20");
    when(discountRepository.findAllByCodeIn(discountCodes)).thenReturn(List.of(validDiscount));

    // Act
    List<Discount> result = discountService.validateAndGetDiscounts(discountCodes);

    // Assert
    assertEquals(1, result.size());
    assertEquals(validDiscount, result.get(0));
    verify(discountRepository).findAllByCodeIn(discountCodes);
  }

  @Test
  @DisplayName("Given missing discount code, when getting discounts by codes, "
      + "then throw InvalidDiscountException")
  void givenMissingDiscountCode_whenGettingDiscountsByCodes_thenThrowInvalidDiscountException() {
    // Arrange
    List<String> discountCodes = List.of("VALID20", "MISSING");
    when(discountRepository.findAllByCodeIn(discountCodes))
        .thenReturn(List.of(validDiscount));

    // Act & Assert
    InvalidDiscountException exception = assertThrows(
        InvalidDiscountException.class,
        () -> discountService.validateAndGetDiscounts(discountCodes));
    assertEquals("Invalid discount code 'MISSING': discount code not found",
        exception.getMessage());
  }

  @Test
  @DisplayName("Given expired discount, when getting discounts by codes, "
      + "then throw InvalidDiscountException")
  void givenExpiredDiscount_whenGettingDiscountsByCodes_thenThrowInvalidDiscountException() {
    // Arrange
    List<String> discountCodes = List.of("EXPIRED20");
    when(discountRepository.findAllByCodeIn(discountCodes))
        .thenReturn(List.of(expiredDiscount));

    // Act & Assert
    InvalidDiscountException exception = assertThrows(
        InvalidDiscountException.class,
        () -> discountService.validateAndGetDiscounts(discountCodes));
    assertEquals("Invalid discount code 'EXPIRED20': discount has expired",
        exception.getMessage());
  }

  @Test
  @DisplayName("Given future discount, when getting discounts by codes, "
      + "then throw InvalidDiscountException")
  void givenFutureDiscount_whenGettingDiscountsByCodes_thenThrowInvalidDiscountException() {
    // Arrange
    List<String> discountCodes = List.of("FUTURE20");
    when(discountRepository.findAllByCodeIn(discountCodes))
        .thenReturn(List.of(futureDiscount));

    // Act & Assert
    InvalidDiscountException exception = assertThrows(
        InvalidDiscountException.class,
        () -> discountService.validateAndGetDiscounts(discountCodes));
    assertEquals("Invalid discount code 'FUTURE20': discount is not yet valid",
        exception.getMessage());
  }

  @Test
  @DisplayName("Given discount with no remaining uses, when getting discounts by codes, "
      + "then throw InvalidDiscountException")
  void givenDiscountWithNoRemainingUses_whenGettingDiscountsByCodes_thenThrowException() {
    // Arrange
    List<String> discountCodes = List.of("NOUSES20");
    when(discountRepository.findAllByCodeIn(discountCodes))
        .thenReturn(List.of(noUsesDiscount));

    // Act & Assert
    InvalidDiscountException exception = assertThrows(
        InvalidDiscountException.class,
        () -> discountService.validateAndGetDiscounts(discountCodes));
    assertEquals("Invalid discount code 'NOUSES20': discount has no remaining uses",
        exception.getMessage());
  }

  @Test
  @DisplayName("Given valid discounts, when updating discount usage, "
      + "then decrement remaining uses and save")
  void givenValidDiscounts_whenUpdatingDiscountUsage_thenDecrementRemainingUsesAndSave() {
    // Arrange
    List<Discount> discounts = List.of(validDiscount);
    int originalUses = validDiscount.getRemainingUses();

    // Act
    discountService.updateDiscountUsage(discounts);

    // Assert
    assertEquals(originalUses - 1, validDiscount.getRemainingUses());
    verify(discountRepository).save(validDiscount);
  }

  @Test
  @DisplayName("Given multiple discounts, when updating discount usage, "
      + "then decrement all remaining uses")
  void givenMultipleDiscounts_whenUpdatingDiscountUsage_thenDecrementAllRemainingUses() {
    // Arrange
    Discount discount2 = new Discount();
    discount2.setRemainingUses(3);
    List<Discount> discounts = List.of(validDiscount, discount2);
    int originalUses1 = validDiscount.getRemainingUses();
    int originalUses2 = discount2.getRemainingUses();

    // Act
    discountService.updateDiscountUsage(discounts);

    // Assert
    assertEquals(originalUses1 - 1, validDiscount.getRemainingUses());
    assertEquals(originalUses2 - 1, discount2.getRemainingUses());
    verify(discountRepository).save(validDiscount);
    verify(discountRepository).save(discount2);
  }

  @Test
  @DisplayName("Given empty discount list, when updating discount usage, then do nothing")
  void givenEmptyDiscountList_whenUpdatingDiscountUsage_thenDoNothing() {
    // Arrange
    List<Discount> emptyDiscounts = List.of();

    // Act
    discountService.updateDiscountUsage(emptyDiscounts);

    // Assert
    verify(discountRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("Given null discount codes, when validating discounts, then return empty list")
  void givenNullDiscountCodes_whenValidatingDiscounts_thenReturnEmptyList() {
    // Act
    List<Discount> result = discountService.validateAndGetDiscounts(null);

    // Assert
    assertEquals(0, result.size());
    verify(discountRepository, never()).findAllByCodeIn(anyList());
  }

  @Test
  @DisplayName("Given empty discount codes, when validating discounts, then return empty list")
  void givenEmptyDiscountCodes_whenValidatingDiscounts_thenReturnEmptyList() {
    // Act
    List<Discount> result = discountService.validateAndGetDiscounts(List.of());

    // Assert
    assertEquals(0, result.size());
    verify(discountRepository, never()).findAllByCodeIn(anyList());
  }

  @Test
  @DisplayName("Given request for all discounts, when getting all discounts, "
      + "then return all discounts")
  void givenRequestForAllDiscounts_whenGettingAllDiscounts_thenReturnAllDiscounts() {
    // Arrange
    List<Discount> allDiscounts = List.of(validDiscount, expiredDiscount);
    when(discountRepository.findAll()).thenReturn(allDiscounts);

    // Act
    List<Discount> result = discountService.getAllDiscounts();

    // Assert
    assertEquals(2, result.size());
    assertEquals(allDiscounts, result);
    verify(discountRepository).findAll();
  }
}
