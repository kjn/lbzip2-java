/*-
 * Copyright (c) 2014 Mikolaj Izdebski
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

import static org.junit.Assert.assertEquals;
import static org.lbzip2.impl.Utils.ilog2;
import static org.lbzip2.impl.Utils.ilog2_16;
import static org.lbzip2.impl.Utils.insertion_sort;
import static org.lbzip2.impl.Utils.isqrt;
import static org.lbzip2.impl.Utils.med3;
import static org.lbzip2.impl.Utils.med5;

import java.util.Arrays;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Mikolaj Izdebski
 */
public class UtilsTest
{
    private final Random r = new Random( 7541 );

    @Test
    public void testIntegerLog2()
    {
        for ( int x = 1; x <= 2 * 1000 * 1000; x++ )
        {
            double expected = Math.floor( Math.log( x ) / Math.log( 2 ) );
            int actual = ilog2( x );

            Assert.assertEquals( "For x=" + x, expected, actual, 0.0000001 );
        }
    }

    @Test
    public void testIntegerLog2_16()
    {
        for ( int x = 1; x <= 65535; x++ )
        {
            double expected = Math.floor( Math.log( x ) / Math.log( 2 ) );
            int actual = ilog2_16( x );

            Assert.assertEquals( "For x=" + x, expected, actual, 0.0000001 );
        }
    }

    @Test
    public void testIntegerSqrt()
    {
        for ( int x = 10; x <= 35000; x++ )
        {
            double expected = Math.floor( Math.sqrt( x ) );
            int actual = isqrt( x, 32768 );

            Assert.assertEquals( "For x=" + x, expected, actual, 0.0000001 );
        }

        int n = 17;

        for ( int x = 10; x <= 1000; x++ )
        {
            double expected = x >= n * n ? n : Math.floor( Math.sqrt( x ) );
            int actual = isqrt( x, n );

            Assert.assertEquals( "For x=" + x, expected, actual, 0.0000001 );
        }
    }

    @Test
    public void testInsertionSort()
    {
        for ( int nRep = 0; nRep < 1000; nRep++ )
        {
            int n = 50 + r.nextInt( 100 );
            long[] A = new long[n];
            for ( int i = 0; i < n; i++ )
                A[i] = r.nextLong();

            long[] B = new long[n];
            System.arraycopy( A, 0, B, 0, n );
            Arrays.sort( B );

            insertion_sort( A, 0, n );

            for ( int i = 0; i < n; i++ )
                assertEquals( B[n - i - 1], A[i] );
        }
    }

    @Test
    public void testMedianOf3_int()
    {
        int[] A = new int[3];

        for ( int v = 0; v < 8; v++ )
        {
            for ( int i = 0; i < 3; i++ )
                A[i] = ( v >> i ) & 1;

            int median = med3( A[0], A[1], A[2] );

            Arrays.sort( A );
            assertEquals( A[1], median );
        }

        for ( int nRep = 0; nRep < 1000; nRep++ )
        {
            for ( int i = 0; i < 3; i++ )
                A[i] = r.nextInt();

            int median = med3( A[0], A[1], A[2] );

            Arrays.sort( A );
            assertEquals( A[1], median );
        }
    }

    @Test
    public void testMedianOf3_long()
    {
        long[] A = new long[3];

        for ( int v = 0; v < 8; v++ )
        {
            for ( int i = 0; i < 3; i++ )
                A[i] = ( v >> i ) & 1;

            long median = med3( A[0], A[1], A[2] );

            Arrays.sort( A );
            assertEquals( A[1], median );
        }

        for ( int nRep = 0; nRep < 1000; nRep++ )
        {
            for ( int i = 0; i < 3; i++ )
                A[i] = r.nextLong();

            long median = med3( A[0], A[1], A[2] );

            Arrays.sort( A );
            assertEquals( A[1], median );
        }
    }

    @Test
    public void testMedianOf5_int()
    {
        int[] A = new int[5];

        for ( int v = 0; v < 32; v++ )
        {
            for ( int i = 0; i < 5; i++ )
                A[i] = ( v >> i ) & 1;

            int median = med5( A[0], A[1], A[2], A[3], A[4] );

            Arrays.sort( A );
            assertEquals( A[2], median );
        }

        for ( int nRep = 0; nRep < 1000; nRep++ )
        {
            for ( int i = 0; i < 5; i++ )
                A[i] = r.nextInt();

            int median = med5( A[0], A[1], A[2], A[3], A[4] );

            Arrays.sort( A );
            assertEquals( A[2], median );
        }
    }

    @Test
    public void testMedianOf5_long()
    {
        long[] A = new long[5];

        for ( int v = 0; v < 32; v++ )
        {
            for ( int i = 0; i < 5; i++ )
                A[i] = ( v >> i ) & 1;

            long median = med5( A[0], A[1], A[2], A[3], A[4] );

            Arrays.sort( A );
            assertEquals( A[2], median );
        }

        for ( int nRep = 0; nRep < 1000; nRep++ )
        {
            for ( int i = 0; i < 5; i++ )
                A[i] = r.nextLong();

            long median = med5( A[0], A[1], A[2], A[3], A[4] );

            Arrays.sort( A );
            assertEquals( A[2], median );
        }
    }
}