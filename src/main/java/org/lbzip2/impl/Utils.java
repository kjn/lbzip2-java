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

import static org.lbzip2.impl.Constants.lg_table;
import static org.lbzip2.impl.Constants.sqq_table;

/**
 * Class implementing various utility methods.
 * 
 * @author Mikolaj Izdebski
 */
final class Utils
{
    /**
     * Compute integer binary logarithm.
     * 
     * @param n logarithm argument, must be positive
     * @return {@code floor(log2(n))}
     */
    static final int ilog2( final int n )
    {
        return ( n & 0xffff0000 ) != 0 ? ( ( n & 0xff000000 ) != 0 ? 24 + lg_table[( n >> 24 ) & 0xff]
                        : 16 + lg_table[( n >> 16 ) & 0xff] )
                        : ( ( n & 0x0000ff00 ) != 0 ? 8 + lg_table[( n >> 8 ) & 0xff] : 0 + lg_table[( n >> 0 ) & 0xff] );
    }

    /**
     * Compute integer binary logarithm. This is a faster version of {@code ilog2}, but it only works for small
     * arguments.
     * 
     * @param n logarithm argument, must be in range from 1 to 65535
     * @return {@code floor(log2(n))}
     */
    static final int ilog2_16( final int n )
    {
        return ( n & 0xff00 ) != 0 ? 8 + lg_table[( n >> 8 ) & 0xff] : 0 + lg_table[( n >> 0 ) & 0xff];
    }

    /**
     * Compute bounded integer square root.
     * 
     * @param x square root argument
     * @param n square root boundary, must be in range from 1 to 32768
     * @return {@code min(floor(sqrt(x)), n)}
     */
    static final int isqrt( final int x, final int n )
    {
        int y, e;

        if ( x >= n * n )
        {
            return n;
        }
        e =
            ( x & 0xffff0000 ) != 0 ? ( ( x & 0xff000000 ) != 0 ? 24 + lg_table[( x >> 24 ) & 0xff]
                            : 16 + lg_table[( x >> 16 ) & 0xff] )
                            : ( ( x & 0x0000ff00 ) != 0 ? 8 + lg_table[( x >> 8 ) & 0xff]
                                            : 0 + lg_table[( x >> 0 ) & 0xff] );

        if ( e >= 16 )
        {
            y = sqq_table[x >> ( ( e - 6 ) - ( e & 1 ) )] << ( ( e >> 1 ) - 7 );
            if ( e >= 24 )
            {
                y = ( y + 1 + x / y ) >> 1;
            }
            y = ( y + 1 + x / y ) >> 1;
        }
        else if ( e >= 8 )
        {
            y = ( sqq_table[x >> ( ( e - 6 ) - ( e & 1 ) )] >> ( 7 - ( e >> 1 ) ) ) + 1;
        }
        else
        {
            return sqq_table[x] >> 4;
        }

        return ( x < ( y * y ) ) ? y - 1 : y;
    }

    /**
     * Sort a subarray of integers in descending order using insertion sort algorithm.
     * 
     * @param P array containing subarray to sort
     * @param first index of first element to sort
     * @param last first index after subarray to sort
     */
    static void insertion_sort( long[] P, int first, int last )
    {
        for ( int a = first + 1; a < last; ++a )
        {
            int b1 = a;
            long t = P[b1];
            for ( int b = b1 - 1; P[b] < t; --b )
            {
                P[b1] = P[b];
                if ( ( b1 = b ) == first )
                    break;
            }
            P[b1] = t;
        }
    }
}
