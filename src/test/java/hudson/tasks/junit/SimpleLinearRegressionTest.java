package hudson.tasks.junit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notANumber;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hudson.tasks.junit.History.SimpleLinearRegression;
import org.junit.jupiter.api.Test;

class SimpleLinearRegressionTest {

    @Test
    void smokes() {
        // Results checked in Excel.
        double[] xs = {2, 3, 4, 5, 6, 8, 10, 11};
        double[] ys = {21.05, 23.51, 24.23, 27.71, 30.86, 45.85, 52.12, 55.98};
        double[] cs = SimpleLinearRegression.coefficients(xs, ys);
        assertThat(cs[0], closeTo(9.4763, 0.0001));
        assertThat(cs[1], closeTo(4.1939, 0.0001));

        xs = new double[] {1.47, 1.5, 1.52, 1.55, 1.57, 1.6, 1.63, 1.65, 1.68, 1.7, 1.73, 1.75, 1.78, 1.8, 1.83};
        ys = new double[] {
            52.21, 53.12, 54.48, 55.84, 57.2, 58.57, 59.93, 61.29, 63.11, 64.47, 66.28, 68.1, 69.92, 72.19, 74.46
        };
        cs = SimpleLinearRegression.coefficients(xs, ys);
        assertThat(cs[0], closeTo(-39.0620, 0.0001));
        assertThat(cs[1], closeTo(61.2722, 0.0001));
    }

    @Test
    void requires2DataPoints() {
        var t = assertThrows(
                IllegalArgumentException.class,
                () -> SimpleLinearRegression.coefficients(new double[0], new double[0]));
        assertThat(t.getMessage(), containsString("At least two data points are required"));
        t = assertThrows(
                IllegalArgumentException.class,
                () -> SimpleLinearRegression.coefficients(new double[1], new double[1]));
        assertThat(t.getMessage(), containsString("At least two data points are required"));
        double[] cs = SimpleLinearRegression.coefficients(new double[] {0.0, 1.0}, new double[] {1.0, 1.0});
        assertThat(cs[0], closeTo(1.0, 0.001));
        assertThat(cs[1], closeTo(0, 0.001));
    }

    @Test
    void requiresArraysWithSameLength() {
        var t = assertThrows(
                IllegalArgumentException.class,
                () -> SimpleLinearRegression.coefficients(new double[3], new double[4]));
        assertThat(t.getMessage(), containsString("Array lengths do not match"));
        t = assertThrows(
                IllegalArgumentException.class,
                () -> SimpleLinearRegression.coefficients(new double[4], new double[3]));
        assertThat(t.getMessage(), containsString("Array lengths do not match"));
    }

    @Test
    void returnsNanIfXValuesDoNotVaryEnough() {
        double[] xs = {Double.MIN_VALUE, 1e162 * Double.MIN_VALUE};
        double[] ys = {0.0, 1.0};
        double[] cs = SimpleLinearRegression.coefficients(xs, ys);
        assertThat(cs[0], notANumber());
        assertThat(cs[1], notANumber());

        xs = new double[] {Double.MIN_VALUE, 1e163 * Double.MIN_VALUE};
        ys = new double[] {0.0, 1.0};
        cs = SimpleLinearRegression.coefficients(xs, ys);
        assertThat(cs[0], closeTo(0.0, 0.001));
        assertThat(cs[1], closeTo(2.0e160, 1e159));
    }
}
