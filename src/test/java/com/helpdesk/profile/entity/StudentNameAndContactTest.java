package com.helpdesk.profile.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Student's name split (given name + surname) and its list of up to three contact numbers. */
class StudentNameAndContactTest {

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

    // surname is nullable on purpose, so a student with one name can register.
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

    @Test
    @DisplayName("Saving numbers trims them and drops blanks and exact duplicates, keeping order")
    void cleansTheNumberList() {
        Student s = new Student();
        s.setContactNumbers(Arrays.asList(" 0771234567 ", "", null, "011 2345678", "0771234567"));

        assertThat(s.getContactNumbers()).containsExactly("0771234567", "011 2345678");
        assertThat(s.getPrimaryContactNumber()).isEqualTo("0771234567");
    }

    // Four boxes with one repeat is three numbers, so it is accepted.
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

    // The profile page has one phone box; editing it must not drop the other numbers.
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
