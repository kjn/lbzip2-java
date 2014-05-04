/*-
 * Copyright (c) 2013 Mikolaj Izdebski
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
 */
package org.lbzip2;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Emulate unsigned integer arithmetic using signed integers.
 * <p>
 * In Java there are no unsigned integer primitive types. Fortunately most of important operations like addition,
 * subtraction and all logical bitwise operations are the same for signed and unsigned integers, so they can be coded
 * directly.
 * <p>
 * One notable exception is unsigned comparison. It needs to be emulated using signed arithmetic operations available in
 * Java. This can be done by adding bias (value equal to {@code Integer.MIN_VALUE} or {@code Long.MIN_VALUE} ) and using
 * normal signed operators. Alternatively unsigned comparison can be emulated by examining sign bit of integer
 * difference.
 */
final class Unsigned
{
    /**
     * Compare two unsigned integers.
     * 
     * @param a
     * @param b
     * @return {@code a < b}
     */
    static final boolean ult( int a, int b )
    {
        return a + Integer.MIN_VALUE < b + Integer.MIN_VALUE;
    }

    /**
     * Compare two unsigned long integers.
     * 
     * @param a
     * @param b
     * @return {@code a < b}
     */
    static final boolean ult( long a, long b )
    {
        return a + Long.MIN_VALUE < b + Long.MIN_VALUE;
    }

    /**
     * Compare two unsigned integers.
     * 
     * @param a
     * @param b
     * @return {@code a <= b}
     */
    static final boolean ule( int a, int b )
    {
        return a + Integer.MIN_VALUE <= b + Integer.MIN_VALUE;
    }

    /**
     * Compare two unsigned long integers.
     * 
     * @param a
     * @param b
     * @return {@code a <= b}
     */
    static final boolean ule( long a, long b )
    {
        return a + Long.MIN_VALUE <= b + Long.MIN_VALUE;
    }

    /**
     * Compare two unsigned integers.
     * 
     * @param a
     * @param b
     * @return {@code a > b}
     */
    static final boolean ugt( int a, int b )
    {
        return a + Integer.MIN_VALUE > b + Integer.MIN_VALUE;
    }

    /**
     * Compare two unsigned long integers.
     * 
     * @param a
     * @param b
     * @return {@code a > b}
     */
    static final boolean ugt( long a, long b )
    {
        return a + Long.MIN_VALUE > b + Long.MIN_VALUE;
    }

    /**
     * Compare two unsigned integers.
     * 
     * @param a
     * @param b
     * @return {@code a >= b}
     */
    static final boolean uge( int a, int b )
    {
        return a + Integer.MIN_VALUE >= b + Integer.MIN_VALUE;
    }

    /**
     * Compare two unsigned long integers.
     * 
     * @param a
     * @param b
     * @return {@code a >= b}
     */
    static final boolean uge( long a, long b )
    {
        return a + Long.MIN_VALUE >= b + Long.MIN_VALUE;
    }

    /**
     * Return minimum of two unsigned integers.
     * 
     * @param a
     * @param b
     * @return {@code a <= b ? a : b}
     */
    static final int umin( int a, int b )
    {
        return min( a + Integer.MIN_VALUE, b + Integer.MIN_VALUE ) + Integer.MIN_VALUE;
    }

    /**
     * Return minimum of two unsigned long integers.
     * 
     * @param a
     * @param b
     * @return {@code a <= b ? a : b}
     */
    static final long umin( long a, long b )
    {
        return min( a + Long.MIN_VALUE, b + Long.MIN_VALUE ) + Long.MIN_VALUE;
    }

    /**
     * Return maximum of two unsigned integers.
     * 
     * @param a
     * @param b
     * @return {@code a >= b ? a : b}
     */
    static final int umax( int a, int b )
    {
        return max( a + Integer.MIN_VALUE, b + Integer.MIN_VALUE ) + Integer.MIN_VALUE;
    }

    /**
     * Return maximum of two unsigned long integers.
     * 
     * @param a
     * @param b
     * @return {@code a >= b ? a : b}
     */
    static final long umax( long a, long b )
    {
        return max( a + Long.MIN_VALUE, b + Long.MIN_VALUE ) + Long.MIN_VALUE;
    }

    private Unsigned()
    {
        // This class is not supposed to be instantiated.
    }
}
