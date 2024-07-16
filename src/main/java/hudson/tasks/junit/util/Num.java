/*
 * Class:        Num
 * Description:  Provides methods to compute some special functions
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
import cern.jet.math.Bessel;

/**
 * This class provides various constants and methods to compute numerical
 * quantities such as factorials, combinations, gamma functions, and so on.
 */
public class Num {

   private Num() {}
   /**
    * Contains the precomputed positive powers of 2. One has
    * TWOEXP[j] = 2^j, for j=0,…,64.
    */
   public static final double TWOEXP[] = {
      1.0, 2.0, 4.0, 8.0, 1.6e1, 3.2e1,
      6.4e1, 1.28e2, 2.56e2, 5.12e2, 1.024e3,
      2.048e3, 4.096e3, 8.192e3, 1.6384e4, 3.2768e4,
      6.5536e4, 1.31072e5, 2.62144e5, 5.24288e5,
      1.048576e6, 2.097152e6, 4.194304e6, 8.388608e6,
      1.6777216e7, 3.3554432e7, 6.7108864e7,
      1.34217728e8, 2.68435456e8, 5.36870912e8,
      1.073741824e9, 2.147483648e9, 4.294967296e9,
      8.589934592e9, 1.7179869184e10, 3.4359738368e10,
      6.8719476736e10, 1.37438953472e11, 2.74877906944e11,
      5.49755813888e11, 1.099511627776e12, 2.199023255552e12,
      4.398046511104e12, 8.796093022208e12,
      1.7592186044416e13, 3.5184372088832e13,
      7.0368744177664e13, 1.40737488355328e14,
      2.81474976710656e14, 5.62949953421312e14,
      1.125899906842624e15, 2.251799813685248e15,
      4.503599627370496e15, 9.007199254740992e15,
      1.8014398509481984e16, 3.6028797018963968e16,
      7.2057594037927936e16, 1.44115188075855872e17,
      2.88230376151711744e17, 5.76460752303423488e17,
      1.152921504606846976e18, 2.305843009213693952e18,
      4.611686018427387904e18, 9.223372036854775808e18,
      1.8446744073709551616e19
     };
}