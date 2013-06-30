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
package org.lbzip2.impl;

import static org.lbzip2.impl.Unsigned.uge;
import static org.lbzip2.impl.Unsigned.ugt;
import static org.lbzip2.impl.Unsigned.ule;
import static org.lbzip2.impl.Unsigned.ult;
import static org.lbzip2.impl.Unsigned.umax;
import static org.lbzip2.impl.Unsigned.umin;

import java.math.BigInteger;
import java.util.Random;

import junit.framework.TestCase;

/**
 * Unit tests for unsigned arithmetic implementation.
 * 
 * @author Mikolaj Izdebski
 */
public class UnsignedTest
    extends TestCase
{
    /**
     * Seed for PRNG.
     */
    private static final long SEED = 7541;

    /**
     * Number of test repetitions (Monte-Carlo iterations).
     */
    private static final int N_ITER = 100000;

    private final Random random = new Random( SEED );

    private BigInteger val( int x )
    {
        long lx = x & ( ( 1L << 32 ) - 1 );
        return new BigInteger( String.valueOf( lx ) );
    }

    private BigInteger val( long x )
    {
        BigInteger lo = val( (int) x );
        BigInteger hi = val( (int) ( x >> 32 ) );
        return hi.shiftLeft( 32 ).or( lo );
    }

    /**
     * Test 32-bit unsigned integer comparison.
     */
    public void testComparison32()
    {
        for ( int iter = 0; iter < N_ITER; iter++ )
        {
            int x = random.nextInt();
            int y = random.nextInt();
            BigInteger bx = val( x );
            BigInteger by = val( y );

            assertEquals( ult( x, y ), bx.compareTo( by ) < 0 );
            assertEquals( ule( x, y ), bx.compareTo( by ) <= 0 );
            assertEquals( ugt( x, y ), bx.compareTo( by ) > 0 );
            assertEquals( uge( x, y ), bx.compareTo( by ) >= 0 );
        }
    }

    /**
     * Test 64-bit unsigned integer comparison.
     */
    public void testComparison64()
    {
        for ( int iter = 0; iter < N_ITER; iter++ )
        {
            long x = random.nextLong();
            long y = random.nextLong();
            BigInteger bx = val( x );
            BigInteger by = val( y );

            assertEquals( ult( x, y ), bx.compareTo( by ) < 0 );
            assertEquals( ule( x, y ), bx.compareTo( by ) <= 0 );
            assertEquals( ugt( x, y ), bx.compareTo( by ) > 0 );
            assertEquals( uge( x, y ), bx.compareTo( by ) >= 0 );
        }
    }

    /**
     * Test 32-bit unsigned integer maximum and minimum functions.
     */
    public void testMinMax32()
    {
        for ( int iter = 0; iter < N_ITER; iter++ )
        {
            int x = random.nextInt();
            int y = random.nextInt();
            BigInteger bx = val( x );
            BigInteger by = val( y );

            assertEquals( val( umin( x, y ) ), bx.min( by ) );
            assertEquals( val( umax( x, y ) ), bx.max( by ) );
        }
    }

    /**
     * Test 64-bit unsigned integer maximum and minimum functions.
     */
    public void testMinMax64()
    {
        for ( int iter = 0; iter < N_ITER; iter++ )
        {
            long x = random.nextLong();
            long y = random.nextLong();
            BigInteger bx = val( x );
            BigInteger by = val( y );

            assertEquals( val( umin( x, y ) ), bx.min( by ) );
            assertEquals( val( umax( x, y ) ), bx.max( by ) );
        }
    }
}
