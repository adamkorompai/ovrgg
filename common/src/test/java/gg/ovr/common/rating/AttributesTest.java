package gg.ovr.common.rating;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AttributesTest {

    @Test
    void acceptsBounds() {
        Attributes attributes = new Attributes(0, 100, 0, 100, 0, 100);

        assertThat(attributes.agr()).isZero();
        assertThat(attributes.frm()).isEqualTo(100);
    }

    @Test
    void rejectsValueAbove100() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Attributes(50, 50, 100.1, 50, 50, 50))
                .withMessageContaining("vis");
    }

    @Test
    void rejectsNegativeValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Attributes(50, 50, 50, 50, -1, 50))
                .withMessageContaining("sur");
    }

    @Test
    void rejectsNaN() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Attributes(Double.NaN, 50, 50, 50, 50, 50))
                .withMessageContaining("agr");
    }
}
