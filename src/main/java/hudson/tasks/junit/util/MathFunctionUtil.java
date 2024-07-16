/*
 * Class:        MathFunctionUtil
 * Description:
 * Environment:  Java
 * Software:     SSJ
 * Copyright (C) 2001  Pierre L'Ecuyer and Universite de Montreal
 * Organization: DIRO, Universite de Montreal
 * @author       Éric Buist
 * @since
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package hudson.tasks.junit.util;

/**
 * Provides utility methods for computing derivatives and integrals of
 * functions.
 */
public class MathFunctionUtil {

/**
 * Step length in x to compute derivatives. Default: 10^{-6}.
 */
public static double H = 1e-6;

   private MathFunctionUtil() {}

   // For Gauss-Lobatto: nodes Cg and weights Wg
   private static final double[] Cg = { 0, 0.17267316464601142812, 0.5,
                                           0.82732683535398857188, 1 };
   private static final double[] Wg = { 0.05, 0.27222222222222222222,
                  0.35555555555555555555, 0.27222222222222222222, 0.05 };


   private static double[] fixBounds (MathFunction func, double a,
                                      double b, int numIntervals) {
   // For functions which are 0 on parts of [a, b], shorten the interval
   // [a, b] to the non-zero part of f(x). Returns the shortened interval.

       final double h = (b - a)/numIntervals;
       double x = b;
       while ((0 == func.evaluate (x)) && (x > a))
           x -= h;
       if (x < b)
           b = x + h;

       x = a;
       while ((0 == func.evaluate (x)) && (x < b))
           x += h;
       if (x > a)
          a = x - h;
       double[] D = {a, b};
       return D;
   }

/**
 * Default number of intervals for Simpson’s integral.
 */
public static int NUMINTERVALS = 1024;

   /**
    * Returns the first derivative of the function `func` evaluated at
    * `x`. If the given function implements
    * @ref MathFunctionWithFirstDerivative, this method calls
    * {@link MathFunctionWithFirstDerivative.derivative()
    * derivative(double)}. Otherwise, if the function implements
    * @ref MathFunctionWithDerivative, this method calls
    * {@link MathFunctionWithDerivative.derivative() derivative(double,
    * int)}. If the function does not implement any of these two
    * interfaces, the method uses
    * {@link #finiteCenteredDifferenceDerivative()
    * finiteCenteredDifferenceDerivative(MathFunction, double, double)} to
    * obtain an estimate of the derivative.
    *  @param func         the function to derivate.
    *  @param x            the evaluation point.
    *  @return the first derivative.
    */
   public static double derivative (MathFunction func, double x) {
      if (func instanceof MathFunctionWithFirstDerivative)
         return ((MathFunctionWithFirstDerivative)func).derivative (x);
      else if (func instanceof MathFunctionWithDerivative)
         return ((MathFunctionWithDerivative)func).derivative (x, 1);
      else
         return finiteCenteredDifferenceDerivative (func, x, H);
   }

   /**
    * Computes and returns an estimate of the nth derivative of the
    * function f(x). This method estimates
    * [
    *   \frac{d^nf(x)}{dx^n},
    * ]
    * the nth derivative of f(x) evaluated at x. This
    * method first computes f_i=f(x+i\epsilon), for i=0,…,n,
    * with \epsilon=h^{1/n}. The estimate is then given by
    * \Delta^nf_0/h, where \Delta^nf_i=\Delta^{n-1}f_{i+1} -
    * \Delta^{n-1}f_i, and \Delta f_i = f_{i+1} - f_i.
    *  @param func         the function to derivate.
    *  @param x            the evaluation point.
    *  @param n            the order of the derivative.
    *  @param h            the error.
    *  @return the estimate of the derivative.
    */
   public static double finiteDifferenceDerivative (
                 MathFunction func, double x, int n, double h) {
      if (n < 0)
         throw new IllegalArgumentException
         ("n must not be negative");
      if (n == 0)
         return func.evaluate (x);
      final double err = Math.pow (h, 1.0 / n);
      final double[] values = new double[n+1];
      for (int i = 0; i < values.length; i++)
         values[i] = func.evaluate (x + i*err);
      for (int j = 0; j < n; j++) {
         for (int i = 0; i < n - j; i++)
            values[i] = values[i + 1] - values[i];
      }
      return values[0] / h;
   }

   /**
    * Returns (f(x + h) - f(x - h))/(2h), an estimate of the first
    * derivative of f(x) using centered differences.
    *  @param func         the function to derivate.
    *  @param x            the evaluation point.
    *  @param h            the error.
    *  @return the estimate of the first derivative.
    */
   public static double finiteCenteredDifferenceDerivative (
                 MathFunction func, double x, double h) {
      final double fplus = func.evaluate (x + h);
      final double fminus = func.evaluate (x - h);
      return (fplus - fminus) / (2*h);
   }

   /**
    * Computes and returns an estimate of the nth derivative of the
    * function f(x) using finite centered differences. If n is
    * even, this method returns
    * {@link #finiteDifferenceDerivative(MathFunction,double,int,double)
    * finiteDifferenceDerivative(func, x - \epsilon*n/2, n, h)}, with
    * h=\epsilon^n.
    *  @param func         the function to derivate.
    *  @param x            the evaluation point.
    *  @param n            the order of the derivative.
    *  @param h            the error.
    *  @return the estimate of the derivative.
    */
   public static double finiteCenteredDifferenceDerivative (
                 MathFunction func, double x, int n, double h) {
      if (n < 0)
         throw new IllegalArgumentException
         ("n must not be negative");
      if (n == 0)
         return func.evaluate (x);
      if (n % 2 == 1)
         throw new IllegalArgumentException ("n must be even");
      final double err = Math.pow (h, 1.0 / n);
      return finiteDifferenceDerivative (func, x - n*err / 2, n, h);
   }

   /**
    * Returns the integral of the function `func` over [a, b]. If
    * the given function implements  @ref MathFunctionWithIntegral, this
    * returns
    * {@link umontreal.ssj.functions.MathFunctionWithIntegral.integral()
    * integral(double, double)}. Otherwise, this calls
    * {@link #simpsonIntegral() simpsonIntegral(MathFunction, double,
    * double, int)} with  #NUMINTERVALS intervals.
    *  @param func         the function to integrate.
    *  @param a            the lower bound.
    *  @param b            the upper bound.
    *  @return the value of the integral.
    */
   public static double integral (MathFunction func, double a, double b) {
      if (func instanceof MathFunctionWithIntegral)
         return ((MathFunctionWithIntegral)func).integral (a, b);
      else
         return simpsonIntegral (func, a, b, NUMINTERVALS);
   }

   /**
    * Computes and returns an approximation of the integral of `func` over
    * [a, b], using the Simpson’s 1/3 method with
    * `numIntervals` intervals. This method estimates
    * [
    *   \int_a^b f(x)dx,
    * ]
    * where f(x) is the function defined by `func` evaluated at
    * x, by dividing [a, b] in n=&nbsp;`numIntervals`
    * intervals of length h=(b - a)/n. The integral is estimated by
    * [
    *   \frac{h}{3}(f(a)+4f(a+h)+2f(a+2h)+4f(a+3h)+\cdots+f(b))
    * ]
    * This method assumes that a\le b<\infty, and n is even.
    *  @param func         the function being integrated.
    *  @param a            the left bound
    *  @param b            the right bound.
    *  @param numIntervals the number of intervals.
    *  @return the approximate value of the integral.
    */
   public static double simpsonIntegral (MathFunction func, double a,
                                         double b, int numIntervals) {
      if (numIntervals % 2 != 0)
         throw new IllegalArgumentException
         ("numIntervals must be an even number");
      if (Double.isInfinite (a) || Double.isInfinite (b) ||
         Double.isNaN (a) || Double.isNaN (b))
         throw new IllegalArgumentException
             ("a and b must not be infinite or NaN");
      if (b < a)
         throw new IllegalArgumentException ("b < a");
      if (a == b)
         return 0;
      double[] D = fixBounds (func, a, b, numIntervals);
      a = D[0];
      b = D[1];
      final double h = (b - a) / numIntervals;
      final double h2 = 2*h;
      final int m = numIntervals / 2;
      double sum = 0;
      for (int i = 0; i < m - 1; i++) {
         final double x = a + h + h2*i;
         sum += 4*func.evaluate (x) + 2*func.evaluate (x + h);
      }
      sum += func.evaluate (a) + func.evaluate (b) + 4*func.evaluate (b - h);
      return sum * h / 3;
   }
}