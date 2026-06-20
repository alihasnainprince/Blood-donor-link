package com.example

import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testBloodGroupValidation() {
    // Valid groups
    assertTrue(FormValidator.isValidBloodGroup("A+"))
    assertTrue(FormValidator.isValidBloodGroup("O-"))
    assertTrue(FormValidator.isValidBloodGroup("AB+"))
    assertTrue(FormValidator.isValidBloodGroup("b-")) // Case insensitive check
    assertTrue(FormValidator.isValidBloodGroup("  O+  ")) // Trimming check

    // Invalid groups
    assertFalse(FormValidator.isValidBloodGroup("X+"))
    assertFalse(FormValidator.isValidBloodGroup("A"))
    assertFalse(FormValidator.isValidBloodGroup("123"))
    assertFalse(FormValidator.isValidBloodGroup(""))
  }

  @Test
  fun testPhoneNumberValidation() {
    // Valid phone structures
    assertTrue(FormValidator.isValidPhoneNumber("+123456789"))
    assertTrue(FormValidator.isValidPhoneNumber("1234567890"))
    assertTrue(FormValidator.isValidPhoneNumber("+880 1712-345 678")) // allows spaces and hyphens
    assertTrue(FormValidator.isValidPhoneNumber("+1 (555) 123-4567")) // parentheses and hyphens allowed

    // Invalid phones
    assertFalse(FormValidator.isValidPhoneNumber("abc"))
    assertFalse(FormValidator.isValidPhoneNumber("123456")) // length too short (min 7)
    assertFalse(FormValidator.isValidPhoneNumber("1234567890123456")) // length too long (max 15 digits)
    assertFalse(FormValidator.isValidPhoneNumber(""))
  }

  @Test
  fun testNameValidation() {
    // Valid names
    assertTrue(FormValidator.isValidName("John Doe"))
    assertTrue(FormValidator.isValidName("Dr. Jane Al-Smith")) // dots, spaces, hyphens, and letters
    assertTrue(FormValidator.isValidName("Ali"))

    // Invalid names
    assertFalse(FormValidator.isValidName("A")) // too short
    assertFalse(FormValidator.isValidName("John Doe 3")) // contains digit
    assertFalse(FormValidator.isValidName("Jane@Home")) // contains special symbol
    assertFalse(FormValidator.isValidName("   "))
  }
}
