/*-
 * Copyright (c) 2012-2014 Mikolaj Izdebski
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

/*-
 * Copyright (c) 2012 Yuta Mori
 * All Rights Reserved.
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */
package org.lbzip2;

import static org.lbzip2.Constants.CHARACTER_BIAS;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Mikolaj Izdebski
 */
final class DivBWT
    implements BWT
{
    private final Logger logger = LoggerFactory.getLogger( DivBWT.class );

    private final SubstringSort SS = new SubstringSort();

    private final TandemRepeatSort TR = new TandemRepeatSort();

    private static final boolean DEBUG = true;

    /*- Settings -*/

    private static final int ALPHABET_SIZE = 256;

    private static final int FIRST_CHAR = -128;

    private static final int LAST_CHAR = 127;

    private static final int BUCKET_A_BIAS = CHARACTER_BIAS + ALPHABET_SIZE * ALPHABET_SIZE;

    private static final int BUCKET_B_BIAS = CHARACTER_BIAS + ALPHABET_SIZE * CHARACTER_BIAS;

    /* for divsufsort.c */
    private final int BUCKET_A( final int[] bucket, final int c0 )
    {
        return bucket[c0 + BUCKET_A_BIAS];
    }

    private final void BUCKET_A_SET( final int[] bucket, final int c0, final int value )
    {
        bucket[c0 + BUCKET_A_BIAS] = value;
    }

    private final void BUCKET_A_INC( final int[] bucket, final int c0 )
    {
        ++bucket[c0 + BUCKET_A_BIAS];
    }

    private final int BUCKET_B( final int[] bucket, final int c0, final int c1 )
    {
        return bucket[( c1 << 8 ) + c0 + BUCKET_B_BIAS];
    }

    private final void BUCKET_B_SET( final int[] bucket, final int c0, final int c1, final int value )
    {
        bucket[( c1 << 8 ) + c0 + BUCKET_B_BIAS] = value;
    }

    private final void BUCKET_B_INC( final int[] bucket, final int c0, final int c1 )
    {
        ++bucket[( c1 << 8 ) + c0 + BUCKET_B_BIAS];
    }

    private final int BUCKET_BSTAR( final int[] bucket, final int c0, final int c1 )
    {
        return bucket[( c0 << 8 ) + c1 + BUCKET_B_BIAS];
    }

    private final void BUCKET_BSTAR_SET( final int[] bucket, final int c0, final int c1, final int value )
    {
        bucket[( c0 << 8 ) + c1 + BUCKET_B_BIAS] = value;
    }

    private final void BUCKET_BSTAR_INC( final int[] bucket, final int c0, final int c1 )
    {
        ++bucket[( c0 << 8 ) + c1 + BUCKET_B_BIAS];
    }

    private final int BUCKET_BSTAR_DEC( final int[] bucket, final int c0, final int c1 )
    {
        return --bucket[( c0 << 8 ) + c1 + BUCKET_B_BIAS];
    }

    /*---- divsufsort ----*/

    /*- Private Functions -*/

    /**
     * Sort suffixes of type B*.
     */
    private final int sort_typeBstar( final byte[] T, final int[] SA, final int[] bucket, final int n )
    {
        int xpa;
        int buf;
        int i, j, k, t, m, bufsize;
        int c0, c1;
        int flag;

        logger.trace( "  Bucket sorting..." );

        /* Initialize bucket arrays. */
        Arrays.fill( bucket, 0 );

        /*
         * Count the number of occurrences of the first one or two characters of each type A, B and B* suffix. Moreover,
         * store the beginning position of all type B* suffixes into the array SA.
         */
        for ( i = 1, flag = 1; i < n; ++i )
        {
            if ( T[i - 1] != T[i] )
            {
                if ( T[i - 1] > T[i] )
                {
                    flag = 0;
                }
                break;
            }
        }
        i = n - 1;
        m = n;
        c0 = T[n - 1];
        c1 = T[0];
        if ( ( c0 < c1 ) || ( ( c0 == c1 ) && ( flag != 0 ) ) )
        {
            if ( flag == 0 )
            {
                BUCKET_BSTAR_INC( bucket, c0, c1 );
                SA[--m] = i;
            }
            else
            {
                BUCKET_B_INC( bucket, c0, c1 );
            }
            for ( --i, c1 = c0; ( 0 <= i ) && ( ( c0 = T[i] ) <= c1 ); --i, c1 = c0 )
            {
                BUCKET_B_INC( bucket, c0, c1 );
            }
        }
        for ( ; 0 <= i; )
        {
            /* type A suffix. */
            do
            {
                BUCKET_A_INC( bucket, c1 = c0 );
            }
            while ( ( 0 <= --i ) && ( ( c0 = T[i] ) >= c1 ) );
            if ( 0 <= i )
            {
                /* type B* suffix. */
                BUCKET_BSTAR_INC( bucket, c0, c1 );
                SA[--m] = i;
                /* type B suffix. */
                for ( --i, c1 = c0; ( 0 <= i ) && ( ( c0 = T[i] ) <= c1 ); --i, c1 = c0 )
                {
                    BUCKET_B_INC( bucket, c0, c1 );
                }
            }
        }
        m = n - m;
        assert ( m <= n / 2 );
        if ( logger.isTraceEnabled() )
        {
            int num_a = 0;
            for ( int c = FIRST_CHAR; c <= LAST_CHAR; c++ )
                num_a += BUCKET_A( bucket, c );
            logger.trace( "    Number of type A  suffixes: {} ({} %)", num_a, String.format( "%.2f", 100.0 * num_a / n ) );
            logger.trace( "    Number of type B  suffixes: {} ({} %)", n - num_a,
                          String.format( "%.2f", 100.0 * ( n - num_a ) / n ) );
            logger.trace( "    Number of type B* suffixes: {} ({} %, {} % of type B suffixes)", m,
                          String.format( "%.2f", 100.0 * m / n ), String.format( "%.2f", 100.0 * m / ( n - num_a ) ) );
        }
        if ( m == 0 )
        {
            return 0;
        }
        /*
         * note: A type B* suffix is lexicographically smaller than a type B suffix that begins with the same first two
         * characters.
         */

        logger.trace( "  Sorting B* suffixes..." );

        /* Calculate the index of start/end point of each bucket. */
        for ( c0 = FIRST_CHAR, i = 0, j = 0; c0 <= LAST_CHAR; ++c0 )
        {
            t = i + BUCKET_A( bucket, c0 );
            BUCKET_A_SET( bucket, c0, i + j ); /* start point */
            i = t + BUCKET_B( bucket, c0, c0 );
            for ( c1 = c0 + 1; c1 <= LAST_CHAR; ++c1 )
            {
                j += BUCKET_BSTAR( bucket, c0, c1 );
                BUCKET_BSTAR_SET( bucket, c0, c1, j ); /* end point */
                i += BUCKET_B( bucket, c0, c1 );
            }
        }

        /* Sort the type B* suffixes by their first two characters. */
        xpa = n - m;
        for ( i = m - 2; 0 <= i; --i )
        {
            t = SA[xpa + i];
            c0 = T[t];
            c1 = T[t + 1];
            SA[BUCKET_BSTAR_DEC( bucket, c0, c1 )] = i;
        }
        t = SA[xpa + m - 1];
        c0 = T[t];
        c1 = T[t + 1];
        SA[BUCKET_BSTAR_DEC( bucket, c0, c1 )] = m - 1;

        /* Sort the type B* substrings using sssort. */
        buf = m;
        bufsize = n - ( 2 * m );
        for ( c0 = LAST_CHAR - 1, j = m; 0 < j; --c0 )
        {
            for ( c1 = LAST_CHAR; c0 < c1; j = i, --c1 )
            {
                i = BUCKET_BSTAR( bucket, c0, c1 );
                if ( 1 < ( j - i ) )
                {
                    SS.sssort( T, SA, xpa, i, j, buf, bufsize, 2, n, SA[i] == ( m - 1 ) );
                }
            }
        }

        /* Compute ranks of type B* substrings. */
        for ( i = m - 1; 0 <= i; --i )
        {
            if ( 0 <= SA[i] )
            {
                j = i;
                do
                {
                    SA[SA[i] + m] = i;
                }
                while ( ( 0 <= --i ) && ( 0 <= SA[i] ) );
                SA[i + 1] = i - j;
                if ( i <= 0 )
                {
                    break;
                }
            }
            j = i;
            do
            {
                SA[( SA[i] = ~SA[i] ) + m] = j;
            }
            while ( SA[--i] < 0 );
            SA[SA[i] + m] = j;
        }

        logger.trace( "  Constructing ISA..." );

        /* Construct the inverse suffix array of type B* suffixes using trsort. */
        TR.trsort( SA, m );

        /* Set the sorted order of type B* suffixes. */
        i = n - 1;
        j = m;
        c0 = T[n - 1];
        c1 = T[0];
        if ( ( c0 < c1 ) || ( ( c0 == c1 ) && ( flag != 0 ) ) )
        {
            t = i;
            for ( --i, c1 = c0; ( 0 <= i ) && ( ( c0 = T[i] ) <= c1 ); --i, c1 = c0 )
            {
            }
            if ( flag == 0 )
            {
                SA[SA[--j + m]] = ( ( 1 < ( t - i ) ) ) ? t : ~t;
            }
        }
        for ( ; 0 <= i; )
        {
            for ( --i, c1 = c0; ( 0 <= i ) && ( ( c0 = T[i] ) >= c1 ); --i, c1 = c0 )
            {
            }
            if ( 0 <= i )
            {
                t = i;
                for ( --i, c1 = c0; ( 0 <= i ) && ( ( c0 = T[i] ) <= c1 ); --i, c1 = c0 )
                {
                }
                SA[SA[--j + m]] = ( ( 1 < ( t - i ) ) ) ? t : ~t;
            }
        }
        if ( SA[SA[m]] == ~0 )
        {
            /* check last type */
            if ( T[n - 1] <= T[0] )
            { /* is type B? */
                SA[SA[m]] = 0;
            }
        }

        if ( DEBUG )
        {
            for ( i = m; i < n; ++i )
            {
                SA[i] = ~n;
            }
        }

        /* Calculate the index of start/end point of each bucket. */
        BUCKET_B_SET( bucket, LAST_CHAR, LAST_CHAR, n ); /* end point */
        for ( c0 = LAST_CHAR - 1, k = m - 1; FIRST_CHAR <= c0; --c0 )
        {
            i = BUCKET_A( bucket, c0 + 1 ) - 1;
            for ( c1 = LAST_CHAR; c0 < c1; --c1 )
            {
                t = i - BUCKET_B( bucket, c0, c1 );
                BUCKET_B_SET( bucket, c0, c1, i ); /* end point */

                /* Move all type B* suffixes to the correct position. */
                for ( i = t, j = BUCKET_BSTAR( bucket, c0, c1 ); j <= k; --i, --k )
                {
                    SA[i] = SA[k];
                }
            }
            BUCKET_BSTAR_SET( bucket, c0, c0 + 1, i - BUCKET_B( bucket, c0, c0 ) + 1 ); /* start point */
            BUCKET_B_SET( bucket, c0, c0, i ); /* end point */
        }

        return m;
    }

    /**
     * Construct BWT transform using sorted order of type B* suffixes.
     */
    private final int construct_BWT( final byte[] T, final int[] SA, final int[] bucket, final int n )
    {
        int i, j, k;
        int s, t, orig = -10;
        int c0, c1, c2;

        logger.trace( "  Constructing BWT..." );

        /*
         * Construct the sorted order of type B suffixes by using the sorted order of type B* suffixes.
         */
        for ( c1 = LAST_CHAR - 1; FIRST_CHAR <= c1; --c1 )
        {
            /* Scan the suffix array from right to left. */
            for ( i = BUCKET_BSTAR( bucket, c1, c1 + 1 ), j = BUCKET_A( bucket, c1 + 1 ) - 1, k = Integer.MIN_VALUE, c2 =
                Integer.MIN_VALUE; i <= j; --j )
            {
                if ( 0 <= ( s = SA[j] ) )
                {
                    assert ( s < n );
                    assert ( T[s] == c1 );
                    assert ( T[s] <= T[( ( s + 1 ) < n ) ? ( s + 1 ) : ( 0 )] );
                    if ( s != 0 )
                    {
                        t = s - 1;
                    }
                    else
                    {
                        t = n - 1;
                        orig = j;
                    }
                    assert ( T[t] <= T[s] );
                    c0 = T[t];
                    SA[j] = ~( c0 + CHARACTER_BIAS );
                    if ( c0 != c2 )
                    {
                        if ( FIRST_CHAR <= c2 )
                        {
                            BUCKET_B_SET( bucket, c2, c1, k );
                        }
                        k = BUCKET_B( bucket, c2 = c0, c1 );
                    }
                    assert ( k < j );
                    SA[k--] = ( ( ( t != 0 ) ? T[t - 1] : T[n - 1] ) > c2 ) ? ~t : t;
                }
                else
                {
                    SA[j] = ~s;
                    assert ( ~s < n );
                }
            }
        }

        /*
         * Construct the BWTed string by using the sorted order of type B suffixes.
         */
        k = BUCKET_A( bucket, c2 = FIRST_CHAR );
        /* Scan the suffix array from left to right. */
        for ( i = 0, j = n; i < j; ++i )
        {
            if ( 0 <= ( s = SA[i] ) )
            {
                if ( s != 0 )
                {
                    t = s - 1;
                }
                else
                {
                    t = n - 1;
                    orig = i;
                }
                assert ( T[t] >= T[s] );
                c0 = T[t];
                SA[i] = c0 + CHARACTER_BIAS;
                if ( c0 != c2 )
                {
                    BUCKET_A_SET( bucket, c2, k );
                    k = BUCKET_A( bucket, c2 = c0 );
                }
                if ( t != 0 )
                {
                    c1 = T[t - 1];
                }
                else
                {
                    c1 = T[n - 1];
                    orig = k;
                }
                assert ( i <= k );
                SA[k++] = ( c1 < c2 ) ? ~( c1 + CHARACTER_BIAS ) : t;
            }
            else
            {
                SA[i] = ~s;
            }
        }

        assert ( orig != -10 );
        assert ( ( 0 <= orig ) && ( orig < n ) );
        return orig;
    }

    /*---------------------------------------------------------------------------*/

    /*- Function -*/

    /**
     * Construct BWT transform of text T[0..n-1] and store it in SA[0..n-1].
     * <p>
     * Length of T must be at least n+1 as T[n] may be used as a sentinel.
     * 
     * @return BWT primary index
     */
    public final int transform( final byte[] T, final int[] SA, final int n )
    {
        int m, pidx, i;

        logger.trace( "Running DivBWT (block size is {})", n );

        /* Check arguments. */
        assert ( n > 0 );
        if ( n == 1 )
        {
            SA[0] = T[0];
            return 0;
        }

        T[n] = T[0];

        final int[] bucket = new int[ALPHABET_SIZE * ALPHABET_SIZE + ALPHABET_SIZE];

        /* Burrows-Wheeler Transform. */
        m = sort_typeBstar( T, SA, bucket, n );
        if ( 0 < m )
        {
            pidx = construct_BWT( T, SA, bucket, n );
        }
        else
        {
            pidx = 0;
            for ( i = 0; i < n; ++i )
            {
                SA[i] = T[0] + CHARACTER_BIAS;
            }
        }

        logger.trace( "DivBWT done (primary index is {})", pidx );
        return pidx;
    }
}
