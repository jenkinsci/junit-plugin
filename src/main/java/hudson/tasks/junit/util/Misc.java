/*
 * Class:        Misc
 * Description:  Miscellaneous functions that are hard to classify.
 * Environment:  Java
 * Software:     SSJ
 * Copyright (C) 2001  Pierre L'Ecuyer and Universite de Montreal
 * Organization: DIRO, Universite de Montreal
 * @author
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
 * This class provides miscellaneous functions that are hard to classify.
 * Some may be moved to another class in the future.
 */
public class Misc {
   private Misc() {}

   /**
    * Returns the k^{th} smallest item of the array A of size
    * n. Array A is unchanged by the method. Restriction: 1
    * \le k \le n.
    *  @param A            the array which contain the items
    *  @param n            the number of items in the array
    *  @param k            the index of the smallest item
    *  @return the kth smallest item of the array A
    */
   public static double quickSelect (double[] A, int n, int k) {
      double[] U = new double[n];
      double[] V = new double[n];
      double p = A[k - 1];
      int u = 0;
      int v = 0;
      int indV = 0;

      for (int i = 0; i < n; i++) {
         if (A[i] <= p) {
            v++;
            if (A[i] != p) {
               U[u++] = A[i];
            }
         } else
            V[indV++] = A[i];
      }

      if (k <= u)
         return quickSelect (U, u, k);
      else if (k > v)
         return quickSelect (V, indV, k - v);
      else return p;
   }

   /**
    * Returns the k^{th} smallest item of the array A of size
    * n. Array A is unchanged by the method. Restriction: 1
    * \le k \le n.
    *  @param A            the array which contain the items
    *  @param n            the number of items in the array
    *  @param k            the index of the smallest item
    *  @return the kth smallest item of the array A
    */
   public static int quickSelect (int[] A, int n, int k) {
      int[] U = new int[n];
      int[] V = new int[n];
      int p = A[k - 1];
      int u = 0;
      int v = 0;
      int indV = 0;

      for (int i = 0; i < n; i++) {
         if (A[i] <= p) {
            v++;
            if (A[i] != p) {
               U[u++] = A[i];
            }
         } else
            V[indV++] = A[i];
      }

      if (k <= u)
         return quickSelect (U, u, k);
      else if (k > v)
         return quickSelect (V, indV, k - v);
      else return p;
   }

   /**
    * Returns the median of the first n elements of array A.
    *  @param A            the array
    *  @param n            the number of used elements
    *  @return the median of A
    */
   public static double getMedian (double[] A, int n) {
      int k = (n+1)/2;     // median index
      double med = quickSelect(A, n, k);
      double y;
      if ((n & 1) == 0) {
         y = quickSelect(A, n, k + 1);
         med = (med + y) / 2.0;
      }
      return med;
   }

   /**
    * Returns the median of the first n elements of array A.
    *  @param A            the array
    *  @param n            the number of used elements
    *  @return the median of A
    */
   public static double getMedian (int[] A, int n) {
      int k = (n+1)/2;     // median index
      double med = quickSelect(A, n, k);
      double y;
      if ((n & 1) == 0) {
         y = quickSelect(A, n, k + 1);
         med = (med + y) / 2.0;
      }
      return med;
   }

   /**
    * Returns the index of the time interval corresponding to time `t`.
    * Let t_0\le\cdots\le t_n be simulation times stored in a
    * subset of `times`. This method uses binary search to determine the
    * smallest value i for which t_i\le t < t_{i+1}, and
    * returns i. The value of t_i is stored in
    * `times[start+i]` whereas n is defined as `end - start`. If
    * t<t_0, this returns -1. If t\ge t_n, this returns
    * n. Otherwise, the returned value is greater than or equal to
    * 0, and smaller than or equal to n-1. `start` and `end` are
    * only used to set lower and upper limits of the search in the `times`
    * array; the index space of the returned value always starts at 0.
    * Note that if the elements of `times` with indices `start`, …, `end`
    * are not sorted in non-decreasing order, the behavior of this method
    * is undefined.
    *  @param times        an array of simulation times.
    *  @param start        the first index in the array to consider.
    *  @param end          the last index (inclusive) in the array to
    *                      consider.
    *  @param t            the queried simulation time.
    *  @return the index of the interval.
    *
    *  @exception NullPointerException if `times` is `null`.
    *  @exception IllegalArgumentException if `start` is negative, or if
    * `end` is smaller than `start`.
    *  @exception ArrayIndexOutOfBoundsException if `start + end` is
    * greater than or equal to the length of `times`.
    */
   public static int getTimeInterval (double[] times, int start, int end,
                                      double t) {
      if (start < 0)
         throw new IllegalArgumentException
            ("The starting index must not be negative");
      int n = end - start;
      if (n < 0)
         throw new IllegalArgumentException
            ("The ending index must be greater than or equal to the starting index");
      if (t < times[start])
         return -1;
      if (t >= times[end])
         return n;

      int start0 = start;
      // Perform binary search to find the interval index
      int mid = (start + end)/2;
      // Test if t is inside the interval mid.
      // The interval mid starts at times[mid],
      // and the interval mid+1 starts at times[mid + 1].
      while (t < times[mid] || t >= times[mid + 1]) {
         if (start == end)
            // Should not happen, safety check to avoid infinite loops.
            throw new IllegalStateException();
         if (t < times[mid])
            // time corresponds to an interval before mid.
            end = mid - 1;
         else
            // time corresponds to an interval after mid.
            start = mid + 1;
         mid = (start + end)/2;
      }
      return mid - start0;
   }

   /**
    * Computes the Newton interpolating polynomial. Given the n+1
    * real distinct points (x_0, y_0), (x_1, y_1),…, (x_n,
    * y_n), with `X[i]` = x_i, `Y[i]` = y_i, this function
    * computes the n+1 coefficients `C[i]` = c_i of the Newton
    * interpolating polynomial P(x) of degree n passing
    * through these points, i.e. such that y_i= P(x_i), given by
    *  REF_util_Misc_eq_newton_interpol
    * [
    *   \qquad P(x) = c_0 + c_1(x-x_0) + c_2(x-x_0)(x-x_1) + \cdots+ c_n(x-x_0)(x-x_1) \cdots(x-x_{n-1}). \tag{eq.newton.interpol}
    * ]
    * @param n            degree of the interpolating polynomial
    *  @param X            x-coordinates of points
    *  @param Y            y-coordinates of points
    *  @param C            Coefficients of the interpolating polynomial
    */
   public static void interpol (int n, double[] X, double[] Y, double[] C) {
      int j;
      // Compute divided differences for the Newton interpolating polynomial
      for (j = 0; j <= n; ++j)
         C[j] = Y[j];
      for (int i = 1; i <= n; ++i)
         for (j = n; j >= i; --j) {
            if (X[j] == X[j-i])
               C[j] = 0;
            else
               C[j] = (C[j] - C[j-1]) / (X[j] - X[j-i]);
         }
   }

   /**
    * Given n, X and C as described in
    * {@link #interpol(int,double[],double[],double[]) interpol(n, X, Y,
    * C)}, this function returns the value of the interpolating polynomial
    * P(z) evaluated at z (see eq.
    * {@link REF_util_Misc_eq_newton_interpol
    * eq.newton.interpol} ).
    *  @param n            degree of the interpolating polynomial
    *  @param X            x-coordinates of points
    *  @param C            Coefficients of the interpolating polynomial
    *  @param z            argument where polynomial is evaluated
    *  @return Value of the interpolating polynomial P(z)
    */
   public static double evalPoly (int n, double[] X, double[] C, double z) {
      double v = C[n];
      for (int j = n-1; j >= 0; --j)
         v = v*(z - X[j]) + C[j];
      return v;
   }

   /**
    * Evaluates the polynomial P(x) of degree n with
    * coefficients c_j = `C[j]` at x:
    *  REF_util_Misc_eq_horner
    * [
    *   \qquad P(x) = c_0 + c_1 x + c_2 x^2 + \cdots+ c_n x^n \tag{eq.horner}
    * ]
    * @param C            Coefficients of the polynomial
    *  @param n            degree of the polynomial
    *  @param x            argument where polynomial is evaluated
    *  @return Value of the polynomial P(x)
    */
   public static double evalPoly (double[] C, int n, double x) {
      double v = C[n];
      for (int j = n-1; j >= 0; --j)
         v = v*x + C[j];
      return v;
   }

}