package com.helpdesk.profile.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The two F1 database requirements that live inside Student itself: the name
 * stored as given name + surname, and more than one contact number.
 *
 * Plain unit tests, no Spring and no database, because the rules under test are
 * pure Java on the entity. They run in milliseconds, so the edge cases that are
 * tedious to click through in a browser - a mononym, a pasted number with
 * spaces round it, a fourth number - are checked on every build instead.
 */
class StudentNameAndContactTest {

    // ---- Name ----

    @Test
    @DisplayName("A full name is split with the last word as the surname")
    void splitsFullNameOnTheLastSpace() {
        Student s = new Student();
        s.setFullName("Shaleel Dakshina Amarasinghe");

        assertThat(s.getGivenName()).isEqualTo("Shaleel Dakshina");
        assertThat(s.getSurname()).isEqualTo("Amarasinghe");
        assertThat(s.getFullName()).isEqualTo("Shaleel Dakshina Amarasinghe");
    }

    @Test
    @DisplayName("Initials-first names keep the initials as the given name")
    void keepsInitialsAsGivenName() {
        Student s = new Student();
        s.setFullName("L. S. N. Perera");

        assertThat(s.getGivenName()).isEqualTo("L. S. N.");
        assertThat(s.getSurname()).isEqualTo("Perera");
    }

    // Why this test exists: surname is nullable precisely so that a student with
    // one name can register. If someone later adds @NotBlank to surname, this
    // fails and says why the rule was left off.
    @Test
    @DisplayName("A single-word name is a given name with no surname")
    void mononymHasNoSurname() {
        Student s = new Student();
        s.setFullName("  Tharmithan  ");

        assertThat(s.getGivenName()).isEqualTo("Tharmithan");
        assertThat(s.getSurname()).isNull();
        assertThat(s.getFullName()).isEqualTo("Tharmithan");
        assertThat(s.getDisplayName()).isEqualTo("Tharmithan");
    }

    @Test
    @DisplayName("Extra spaces inside a name do not create empty name parts")
    void collapsesRepeatedSpaces() {
        Student s = new Student();
        s.setFullName("Diro    Ruban");

        assertThat(s.getGivenName()).isEqualTo("Diro");
        assertThat(s.getSurname()).isEqualTo("Ruban");
    }

    @Test
    @DisplayName("A blank surname is stored as null, not as an empty string")
    void blankSurnameBecomesNull() {
        Student s = new Student();
        s.setGivenName("Kasun");
        s.setSurname("   ");

        assertThat(s.getSurname()).isNull();
        assertThat(s.getFullName()).isEqualTo("Kasun");
    }

    // ---- Contact numbers ----

    @Test
    @DisplayName("Saving numbers trims them and drops blanks and exact duplicates, keeping order")
    void cleansTheNumberList() {
        Student s = new Student();
        s.setContactNumbers(Arrays.asList(" 0771234567 ", "", null, "011 2345678", "0771234567"));

        assertThat(s.getContactNumbers()).containsExactly("0771234567", "011 2345678");
        assertThat(s.getPrimaryContactNumber()).isEqualTo("0771234567");
    }

    // The limit is on the CLEANED list: four boxes where one repeats another is
    // three numbers, and must be accepted.
    @Test
    @DisplayName("Three real numbers are accepted even if a duplicate was also sent")
    void limitAppliesAfterCleaning() {
        Student s = new Student();
        s.setContactNumbers(List.of("0711111111", "0712222222", "0713333333", "0711111111"));

        assertThat(s.getContactNumbers()).hasSize(3);
    }

    @Test
    @DisplayName("A fourth distinct number is refused")
    void refusesAFourthNumber() {
        Student s = new Student();

        assertThatThrownBy(() ->
                s.setContactNumbers(List.of("0711111111", "0712222222", "0713333333", "0714444444")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At most three");
    }

    // Why this test exists: the current profile page has ONE phone box. Editing
    // it must not silently delete the second number a student saved elsewhere.
    @Test
    @DisplayName("Editing the single phone box changes the first number and keeps the rest")
    void primaryNumberEditKeepsOthers() {
        Student s = new Student();
        s.setContactNumbers(List.of("0771234567", "0112345678"));

        s.setPrimaryContactNumber("0719999999");

        assertThat(s.getContactNumbers()).containsExactly("0719999999", "0112345678");
    }

    @Test
    @DisplayName("Clearing the phone box removes only the first number")
    void clearingPrimaryPromotesTheNext() {
        Student s = new Student();
        s.setContactNumbers(List.of("0771234567", "0112345678"));

        s.setPrimaryContactNumber("");

        assertThat(s.getContactNumbers()).containsExactly("0112345678");
    }
}
