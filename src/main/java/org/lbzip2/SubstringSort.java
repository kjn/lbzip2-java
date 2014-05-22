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

import static java.lang.Math.min;
import static org.lbzip2.Utils.ilog2_16;
import static org.lbzip2.Utils.isqrt;
import static org.lbzip2.Utils.med3;
import static org.lbzip2.Utils.med5;

/**
 * A class implementing substring sort used in construction of Burrows-Wheeler transform.
 * 
 * @author Mikolaj Izdebski
 */
final class SubstringSort
{
    /**
     * Threshold for using insertion sort instead of quicksort. Substring sets not larger than this number will always
     * be sorted using insertion sort algorithm.
     */
    private static final int SS_INSERTIONSORT_THRESHOLD = 8;

    /**
     * Maximal length of each substring. Suffixes which have common prefix of {@code SS_BLOCKSIZE} characters (or more)
     * are considered as equal by this algorithm, their ordering is established using tandem repeat sort.
     */
    private static final int SS_BLOCKSIZE = 1024;

    /*- Macros -*/
    private final int STACK_PUSH( final int[] stack, final int ssize, final int a, final int b, final int c, final int d )
    {
        stack[ssize] = a;
        stack[ssize + 1] = b;
        stack[ssize + 2] = c;
        stack[ssize + 3] = d;
        return ssize + 4;
    }

    /*---------------------------------------------------------------------------*/

    /* Compares two suffixes. */
    private final int ss_compare( final byte[] T, final int[] SA, final int p1, final int p2, final int depth )
    {
        int U1, U2, U1n, U2n;

        for ( U1 = depth + SA[p1], U2 = depth + SA[p2], U1n = SA[p1 + 1] + 2, U2n = SA[p2 + 1] + 2; ( U1 < U1n )
            && ( U2 < U2n ) && ( T[U1] == T[U2] ); ++U1, ++U2 )
        {
        }

        return U1 < U1n ? ( U2 < U2n ? T[U1] - T[U2] : 1 ) : ( U2 < U2n ? -1 : 0 );
    }

    private final int ss_compare_last( final byte[] T, final int[] SA, final int xpa, final int p1, final int p2,
                                       final int depth, final int size )
    {
        int U1, U2, U1n, U2n;

        for ( U1 = depth + SA[p1], U2 = depth + SA[p2], U1n = size, U2n = SA[p2 + 1] + 2; ( U1 < U1n ) && ( U2 < U2n )
            && ( T[U1] == T[U2] ); ++U1, ++U2 )
        {
        }

        if ( U1 < U1n )
        {
            return ( U2 < U2n ) ? T[U1] - T[U2] : 1;
        }
        else if ( U2 == U2n )
        {
            return 1;
        }

        for ( U1 = U1 % size, U1n = SA[xpa] + 2; ( U1 < U1n ) && ( U2 < U2n ) && ( T[U1] == T[U2] ); ++U1, ++U2 )
        {
        }

        return U1 < U1n ? ( U2 < U2n ? T[U1] - T[U2] : 1 ) : ( U2 < U2n ? -1 : 0 );

    }

    /*---------------------------------------------------------------------------*/

    /* Insertionsort for small size groups */
    private final void ss_insertionsort( final byte[] T, final int[] SA, final int xpa, final int first,
                                         final int last, final int depth )
    {
        int i, j;
        int t;
        int r;

        for ( i = last - 2; first <= i; --i )
        {
            for ( t = SA[i], j = i + 1; 0 < ( r = ss_compare( T, SA, xpa + t, xpa + SA[j], depth ) ); )
            {
                do
                {
                    SA[j - 1] = SA[j];
                }
                while ( ( ++j < last ) && ( SA[j] < 0 ) );
                if ( last <= j )
                {
                    break;
                }
            }
            if ( r == 0 )
            {
                SA[j] = ~SA[j];
            }
            SA[j - 1] = t;
        }
    }

    /*---------------------------------------------------------------------------*/

    private final void ss_fixdown( final byte[] T, final int depth, final int[] SA, final int xpa, final int root,
                                   int i, final int size )
    {
        int j, k;
        int v;
        int c, d, e;

        for ( v = SA[root + i], c = T[SA[xpa + v] + depth]; ( j = 2 * i + 1 ) < size; SA[root + i] = SA[root + k], i =
            k )
        {
            d = T[SA[xpa + SA[root + ( k = j++ )]] + depth];
            if ( d < ( e = T[SA[xpa + SA[root + j]] + depth] ) )
            {
                k = j;
                d = e;
            }
            if ( d <= c )
            {
                break;
            }
        }
        SA[root + i] = v;
    }

    /* Simple top-down heapsort. */
    private final void ss_heapsort( final byte[] T, final int depth, final int[] SA, final int xpa, final int root,
                                    final int size )
    {
        int i, m;
        int t;

        m = size;
        if ( ( size % 2 ) == 0 )
        {
            m--;
            if ( T[SA[xpa + SA[root + m / 2]] + depth] < T[SA[xpa + SA[root + m]] + depth] )
            {
                t = SA[root + m];
                SA[root + m] = SA[root + m / 2];
                SA[root + m / 2] = t;
            }
        }

        for ( i = m / 2 - 1; 0 <= i; --i )
        {
            ss_fixdown( T, depth, SA, xpa, root, i, m );
        }
        if ( ( size % 2 ) == 0 )
        {
            t = SA[root];
            SA[root] = SA[root + m];
            SA[root + m] = t;
            ss_fixdown( T, depth, SA, xpa, root, 0, m );
        }
        for ( i = m - 1; 0 < i; --i )
        {
            t = SA[root];
            SA[root] = SA[root + i];
            ss_fixdown( T, depth, SA, xpa, root, 0, i );
            SA[root + i] = t;
        }
    }

    private final int ss_pivot_key( final byte[] T, final int depth, final int[] SA, final int xpa, final int p )
    {
        return ( T[SA[xpa + SA[p]] + depth] << 24 ) + p;
    }

    /* Returns the pivot element. */
    private final int ss_pivot( final byte[] T, final int depth, final int[] SA, final int xpa, int first, int last )
    {
        int t = ( last - first ) >> 1;
        final int middle = first + t;

        final int v1 = ss_pivot_key( T, depth, SA, xpa, first );
        final int v5 = ss_pivot_key( T, depth, SA, xpa, middle );
        final int v9 = ss_pivot_key( T, depth, SA, xpa, last - 1 );

        if ( t <= 16 )
        {
            return med3( v1, v5, v9 ) & 0xFFFFFF;
        }

        t >>= 1;
        final int v3 = ss_pivot_key( T, depth, SA, xpa, first + t );
        final int v7 = ss_pivot_key( T, depth, SA, xpa, last - t );

        if ( t <= 128 )
        {
            return med5( v1, v3, v5, v7, v9 ) & 0xFFFFFF;
        }

        t >>= 1;
        final int v2 = ss_pivot_key( T, depth, SA, xpa, first + t );
        final int v4 = ss_pivot_key( T, depth, SA, xpa, middle - t );
        final int v6 = ss_pivot_key( T, depth, SA, xpa, middle + t );
        final int v8 = ss_pivot_key( T, depth, SA, xpa, last - t );

        return med3( med3( v1, v2, v3 ), med3( v4, v5, v6 ), med3( v7, v8, v9 ) ) & 0xFFFFFF;
    }

    /*---------------------------------------------------------------------------*/

    /* Binary partition for substrings. */
    private final int ss_partition( final int[] SA, final int xpa, final int first, final int last, final int depth )
    {
        int a, b;
        int t;
        for ( a = first - 1, b = last;; )
        {
            for ( ; ( ++a < b ) && ( ( SA[xpa + SA[a]] + depth ) >= ( SA[xpa + SA[a] + 1] + 1 ) ); )
            {
                SA[a] = ~SA[a];
            }
            for ( ; ( a < --b ) && ( ( SA[xpa + SA[b]] + depth ) < ( SA[xpa + SA[b] + 1] + 1 ) ); )
            {
            }
            if ( b <= a )
            {
                break;
            }
            t = ~SA[b];
            SA[b] = SA[a];
            SA[a] = t;
        }
        if ( first < a )
        {
            SA[first] = ~SA[first];
        }
        return a;
    }

    /* Multikey introsort for medium size groups. */
    private final void ss_mintrosort( final byte[] T, final int[] SA, final int xpa, int first, int last, int depth )
    {
        /* minstacksize = log(SS_BLOCKSIZE) / log(3) * 2 */
        final int SS_MISORT_STACKSIZE = 16;
        final int[] stack = new int[4 * SS_MISORT_STACKSIZE];
        int a, b, c, d, e, f;
        int s, t;
        int ssize;
        int limit;
        int v, x = 0;

        for ( ssize = 0, limit = ilog2_16( last - first );; )
        {

            if ( ( last - first ) <= SS_INSERTIONSORT_THRESHOLD )
            {
                if ( 1 < ( last - first ) )
                {
                    ss_insertionsort( T, SA, xpa, first, last, depth );
                }
                // STACK_POP(first, last, depth, limit)
                if ( ssize == 0 )
                    return;
                first = stack[ssize - 4];
                last = stack[ssize - 3];
                depth = stack[ssize - 2];
                limit = stack[ssize - 1];
                ssize -= 4;
                continue;
            }

            if ( limit-- == 0 )
            {
                ss_heapsort( T, depth, SA, xpa, first, last - first );
            }
            if ( limit < 0 )
            {
                for ( a = first + 1, v = T[SA[xpa + SA[first]] + depth]; a < last; ++a )
                {
                    if ( ( x = T[SA[xpa + SA[a]] + depth] ) != v )
                    {
                        if ( 1 < ( a - first ) )
                        {
                            break;
                        }
                        v = x;
                        first = a;
                    }
                }
                if ( T[SA[xpa + SA[first]] - 1 + depth] < v )
                {
                    first = ss_partition( SA, xpa, first, a, depth );
                }
                if ( ( a - first ) <= ( last - a ) )
                {
                    if ( 1 < ( a - first ) )
                    {
                        ssize = STACK_PUSH( stack, ssize, a, last, depth, -1 );
                        last = a;
                        depth += 1;
                        limit = ilog2_16( a - first );
                    }
                    else
                    {
                        first = a;
                        limit = -1;
                    }
                }
                else
                {
                    if ( 1 < ( last - a ) )
                    {
                        ssize = STACK_PUSH( stack, ssize, first, a, depth + 1, ilog2_16( a - first ) );
                        first = a;
                        limit = -1;
                    }
                    else
                    {
                        last = a;
                        depth += 1;
                        limit = ilog2_16( a - first );
                    }
                }
                continue;
            }

            /* choose pivot */
            a = ss_pivot( T, depth, SA, xpa, first, last );
            v = T[SA[xpa + SA[a]] + depth];
            t = SA[first];
            SA[first] = SA[a];
            SA[a] = t;

            /* partition */
            for ( b = first; ( ++b < last ) && ( ( x = T[SA[xpa + SA[b]] + depth] ) == v ); )
            {
            }
            if ( ( ( a = b ) < last ) && ( x < v ) )
            {
                for ( ; ( ++b < last ) && ( ( x = T[SA[xpa + SA[b]] + depth] ) <= v ); )
                {
                    if ( x == v )
                    {
                        t = SA[b];
                        SA[b] = SA[a];
                        SA[a] = t;
                        ++a;
                    }
                }
            }
            for ( c = last; ( b < --c ) && ( ( x = T[SA[xpa + SA[c]] + depth] ) == v ); )
            {
            }
            if ( ( b < ( d = c ) ) && ( x > v ) )
            {
                for ( ; ( b < --c ) && ( ( x = T[SA[xpa + SA[c]] + depth] ) >= v ); )
                {
                    if ( x == v )
                    {
                        t = SA[c];
                        SA[c] = SA[d];
                        SA[d] = t;
                        --d;
                    }
                }
            }
            for ( ; b < c; )
            {
                t = SA[b];
                SA[b] = SA[c];
                SA[c] = t;
                for ( ; ( ++b < c ) && ( ( x = T[SA[xpa + SA[b]] + depth] ) <= v ); )
                {
                    if ( x == v )
                    {
                        t = SA[b];
                        SA[b] = SA[a];
                        SA[a] = t;
                        ++a;
                    }
                }
                for ( ; ( b < --c ) && ( ( x = T[SA[xpa + SA[c]] + depth] ) >= v ); )
                {
                    if ( x == v )
                    {
                        t = SA[c];
                        SA[c] = SA[d];
                        SA[d] = t;
                        --d;
                    }
                }
            }

            if ( a <= d )
            {
                c = b - 1;

                if ( ( s = a - first ) > ( t = b - a ) )
                {
                    s = t;
                }
                for ( e = first, f = b - s; 0 < s; --s, ++e, ++f )
                {
                    t = SA[e];
                    SA[e] = SA[f];
                    SA[f] = t;
                }
                if ( ( s = d - c ) > ( t = last - d - 1 ) )
                {
                    s = t;
                }
                for ( e = b, f = last - s; 0 < s; --s, ++e, ++f )
                {
                    t = SA[e];
                    SA[e] = SA[f];
                    SA[f] = t;
                }

                a = first + ( b - a );
                c = last - ( d - c );
                b = ( v <= T[SA[xpa + SA[a]] - 1 + depth] ) ? a : ss_partition( SA, xpa, a, c, depth );

                if ( ( a - first ) <= ( last - c ) )
                {
                    if ( ( last - c ) <= ( c - b ) )
                    {
                        ssize = STACK_PUSH( stack, ssize, b, c, depth + 1, ilog2_16( c - b ) );
                        ssize = STACK_PUSH( stack, ssize, c, last, depth, limit );
                        last = a;
                    }
                    else if ( ( a - first ) <= ( c - b ) )
                    {
                        ssize = STACK_PUSH( stack, ssize, c, last, depth, limit );
                        ssize = STACK_PUSH( stack, ssize, b, c, depth + 1, ilog2_16( c - b ) );
                        last = a;
                    }
                    else
                    {
                        ssize = STACK_PUSH( stack, ssize, c, last, depth, limit );
                        ssize = STACK_PUSH( stack, ssize, first, a, depth, limit );
                        first = b;
                        last = c;
                        depth += 1;
                        limit = ilog2_16( c - b );
                    }
                }
                else
                {
                    if ( ( a - first ) <= ( c - b ) )
                    {
                        ssize = STACK_PUSH( stack, ssize, b, c, depth + 1, ilog2_16( c - b ) );
                        ssize = STACK_PUSH( stack, ssize, first, a, depth, limit );
                        first = c;
                    }
                    else if ( ( last - c ) <= ( c - b ) )
                    {
                        ssize = STACK_PUSH( stack, ssize, first, a, depth, limit );
                        ssize = STACK_PUSH( stack, ssize, b, c, depth + 1, ilog2_16( c - b ) );
                        first = c;
                    }
                    else
                    {
                        ssize = STACK_PUSH( stack, ssize, first, a, depth, limit );
                        ssize = STACK_PUSH( stack, ssize, c, last, depth, limit );
                        first = b;
                        last = c;
                        depth += 1;
                        limit = ilog2_16( c - b );
                    }
                }
            }
            else
            {
                limit += 1;
                if ( T[SA[xpa + SA[first]] - 1 + depth] < v )
                {
                    first = ss_partition( SA, xpa, first, last, depth );
                    limit = ilog2_16( last - first );
                }
                depth += 1;
            }
        }
    }

    /*---------------------------------------------------------------------------*/

    private final void ss_blockswap( final int[] SA, int a, int b, int n )
    {
        int t;
        for ( ; 0 < n; --n, ++a, ++b )
        {
            t = SA[a];
            SA[a] = SA[b];
            SA[b] = t;
        }
    }

    private final void ss_rotate( final int[] SA, int first, final int middle, int last )
    {
        int a, b, t;
        int l, r;
        l = middle - first;
        r = last - middle;
        for ( ; ( 0 < l ) && ( 0 < r ); )
        {
            if ( l == r )
            {
                ss_blockswap( SA, first, middle, l );
                break;
            }
            if ( l < r )
            {
                a = last - 1;
                b = middle - 1;
                t = SA[a];
                do
                {
                    SA[a--] = SA[b];
                    SA[b--] = SA[a];
                    if ( b < first )
                    {
                        SA[a] = t;
                        last = a;
                        if ( ( r -= l + 1 ) <= l )
                        {
                            break;
                        }
                        a -= 1;
                        b = middle - 1;
                        t = SA[a];
                    }
                }
                while ( true );
            }
            else
            {
                a = first;
                b = middle;
                t = SA[a];
                do
                {
                    SA[a++] = SA[b];
                    SA[b++] = SA[a];
                    if ( last <= b )
                    {
                        SA[a] = t;
                        first = a + 1;
                        if ( ( l -= r + 1 ) <= r )
                        {
                            break;
                        }
                        a += 1;
                        b = middle;
                        t = SA[a];
                    }
                }
                while ( true );
            }
        }
    }

    /*---------------------------------------------------------------------------*/

    private final void ss_inplacemerge( final byte[] T, final int[] SA, final int xpa, final int first, int middle,
                                        int last, final int depth )
    {
        int p;
        int a, b;
        int len, half;
        int q, r;
        int x;

        for ( ;; )
        {
            if ( SA[last - 1] < 0 )
            {
                x = 1;
                p = xpa + ~SA[last - 1];
            }
            else
            {
                x = 0;
                p = xpa + SA[last - 1];
            }
            for ( a = first, len = middle - first, half = len >> 1, r = -1; 0 < len; len = half, half >>= 1 )
            {
                b = a + half;
                q = ss_compare( T, SA, xpa + ( ( 0 <= SA[b] ) ? SA[b] : ~SA[b] ), p, depth );
                if ( q < 0 )
                {
                    a = b + 1;
                    half -= ( len & 1 ) ^ 1;
                }
                else
                {
                    r = q;
                }
            }
            if ( a < middle )
            {
                if ( r == 0 )
                {
                    SA[a] = ~SA[a];
                }
                ss_rotate( SA, a, middle, last );
                last -= middle - a;
                middle = a;
                if ( first == middle )
                {
                    break;
                }
            }
            --last;
            if ( x != 0 )
            {
                while ( SA[--last] < 0 )
                {
                }
            }
            if ( middle == last )
            {
                break;
            }
        }
    }

    /*---------------------------------------------------------------------------*/

    /* Merge-forward with internal buffer. */
    private final void ss_mergeforward( final byte[] T, final int[] SA, final int xpa, final int first,
                                        final int middle, final int last, final int buf, final int depth )
    {
        int a, b, c, bufend;
        int t;
        int r;

        bufend = buf + ( middle - first ) - 1;
        ss_blockswap( SA, buf, first, middle - first );

        for ( t = SA[a = first], b = buf, c = middle;; )
        {
            r = ss_compare( T, SA, xpa + SA[b], xpa + SA[c], depth );
            if ( r < 0 )
            {
                do
                {
                    SA[a++] = SA[b];
                    if ( bufend <= b )
                    {
                        SA[bufend] = t;
                        return;
                    }
                    SA[b++] = SA[a];
                }
                while ( SA[b] < 0 );
            }
            else if ( r > 0 )
            {
                do
                {
                    SA[a++] = SA[c];
                    SA[c++] = SA[a];
                    if ( last <= c )
                    {
                        while ( b < bufend )
                        {
                            SA[a++] = SA[b];
                            SA[b++] = SA[a];
                        }
                        SA[a] = SA[b];
                        SA[b] = t;
                        return;
                    }
                }
                while ( SA[c] < 0 );
            }
            else
            {
                SA[c] = ~SA[c];
                do
                {
                    SA[a++] = SA[b];
                    if ( bufend <= b )
                    {
                        SA[bufend] = t;
                        return;
                    }
                    SA[b++] = SA[a];
                }
                while ( SA[b] < 0 );

                do
                {
                    SA[a++] = SA[c];
                    SA[c++] = SA[a];
                    if ( last <= c )
                    {
                        while ( b < bufend )
                        {
                            SA[a++] = SA[b];
                            SA[b++] = SA[a];
                        }
                        SA[a] = SA[b];
                        SA[b] = t;
                        return;
                    }
                }
                while ( SA[c] < 0 );
            }
        }
    }

    /* Merge-backward with internal buffer. */
    private final void ss_mergebackward( final byte[] T, final int[] SA, final int xpa, final int first,
                                         final int middle, final int last, final int buf, final int depth )
    {
        int p1, p2;
        int a, b, c, bufend;
        int t;
        int r;
        int x;

        bufend = buf + ( last - middle ) - 1;
        ss_blockswap( SA, buf, middle, last - middle );

        x = 0;
        if ( SA[bufend] < 0 )
        {
            p1 = xpa + ~SA[bufend];
            x |= 1;
        }
        else
        {
            p1 = xpa + SA[bufend];
        }
        if ( SA[middle - 1] < 0 )
        {
            p2 = xpa + ~SA[middle - 1];
            x |= 2;
        }
        else
        {
            p2 = xpa + SA[middle - 1];
        }
        for ( t = SA[a = last - 1], b = bufend, c = middle - 1;; )
        {
            r = ss_compare( T, SA, p1, p2, depth );
            if ( 0 < r )
            {
                if ( ( x & 1 ) != 0 )
                {
                    do
                    {
                        SA[a--] = SA[b];
                        SA[b--] = SA[a];
                    }
                    while ( SA[b] < 0 );
                    x ^= 1;
                }
                SA[a--] = SA[b];
                if ( b <= buf )
                {
                    SA[buf] = t;
                    break;
                }
                SA[b--] = SA[a];
                if ( SA[b] < 0 )
                {
                    p1 = xpa + ~SA[b];
                    x |= 1;
                }
                else
                {
                    p1 = xpa + SA[b];
                }
            }
            else if ( r < 0 )
            {
                if ( ( x & 2 ) != 0 )
                {
                    do
                    {
                        SA[a--] = SA[c];
                        SA[c--] = SA[a];
                    }
                    while ( SA[c] < 0 );
                    x ^= 2;
                }
                SA[a--] = SA[c];
                SA[c--] = SA[a];
                if ( c < first )
                {
                    while ( buf < b )
                    {
                        SA[a--] = SA[b];
                        SA[b--] = SA[a];
                    }
                    SA[a] = SA[b];
                    SA[b] = t;
                    break;
                }
                if ( SA[c] < 0 )
                {
                    p2 = xpa + ~SA[c];
                    x |= 2;
                }
                else
                {
                    p2 = xpa + SA[c];
                }
            }
            else
            {
                if ( ( x & 1 ) != 0 )
                {
                    do
                    {
                        SA[a--] = SA[b];
                        SA[b--] = SA[a];
                    }
                    while ( SA[b] < 0 );
                    x ^= 1;
                }
                SA[a--] = ~SA[b];
                if ( b <= buf )
                {
                    SA[buf] = t;
                    break;
                }
                SA[b--] = SA[a];
                if ( ( x & 2 ) != 0 )
                {
                    do
                    {
                        SA[a--] = SA[c];
                        SA[c--] = SA[a];
                    }
                    while ( SA[c] < 0 );
                    x ^= 2;
                }
                SA[a--] = SA[c];
                SA[c--] = SA[a];
                if ( c < first )
                {
                    while ( buf < b )
                    {
                        SA[a--] = SA[b];
                        SA[b--] = SA[a];
                    }
                    SA[a] = SA[b];
                    SA[b] = t;
                    break;
                }
                if ( SA[b] < 0 )
                {
                    p1 = xpa + ~SA[b];
                    x |= 1;
                }
                else
                {
                    p1 = xpa + SA[b];
                }
                if ( SA[c] < 0 )
                {
                    p2 = xpa + ~SA[c];
                    x |= 2;
                }
                else
                {
                    p2 = xpa + SA[c];
                }
            }
        }
    }

    private final int GETIDX( final int a )
    {
        return 0 <= a ? a : ~a;
    }

    private final void MERGE_CHECK( final byte[] T, final int[] SA, final int xpa, final int depth, final int a,
                                    final int b, final int c )
    {
        if ( ( c & 1 ) != 0
            || ( ( c & 2 ) != 0 && ( ss_compare( T, SA, xpa + GETIDX( SA[a - 1] ), xpa + SA[a], depth ) == 0 ) ) )
        {
            SA[a] = ~SA[a];
        }
        if ( ( c & 4 ) != 0 && ( ( ss_compare( T, SA, xpa + GETIDX( SA[b - 1] ), xpa + SA[b], depth ) == 0 ) ) )
        {
            SA[b] = ~SA[b];
        }
    }

    /* D&C based merge. */
    private final void ss_swapmerge( final byte[] T, final int[] SA, final int xpa, int first, int middle, int last,
                                     final int buf, final int bufsize, final int depth )
    {
        final int SS_SMERGE_STACKSIZE = 32;
        final int[] stack = new int[4 * SS_SMERGE_STACKSIZE];
        int l, r, lm, rm;
        int m, len, half;
        int ssize;
        int check, next;

        for ( check = 0, ssize = 0;; )
        {
            if ( ( last - middle ) <= bufsize )
            {
                if ( ( first < middle ) && ( middle < last ) )
                {
                    ss_mergebackward( T, SA, xpa, first, middle, last, buf, depth );
                }
                MERGE_CHECK( T, SA, xpa, depth, first, last, check );
                // STACK_POP(first, middle, last, check)
                if ( ssize == 0 )
                    return;
                first = stack[ssize - 4];
                middle = stack[ssize - 3];
                last = stack[ssize - 2];
                check = stack[ssize - 1];
                ssize -= 4;
                continue;
            }

            if ( ( middle - first ) <= bufsize )
            {
                if ( first < middle )
                {
                    ss_mergeforward( T, SA, xpa, first, middle, last, buf, depth );
                }
                MERGE_CHECK( T, SA, xpa, depth, first, last, check );
                // STACK_POP(first, middle, last, check)
                if ( ssize == 0 )
                    return;
                first = stack[ssize - 4];
                middle = stack[ssize - 3];
                last = stack[ssize - 2];
                check = stack[ssize - 1];
                ssize -= 4;
                continue;
            }

            for ( m = 0, len = min( middle - first, last - middle ), half = len >> 1; 0 < len; len = half, half >>= 1 )
            {
                if ( ss_compare( T, SA, xpa + GETIDX( SA[middle + m + half] ),
                                 xpa + GETIDX( SA[middle - m - half - 1] ), depth ) < 0 )
                {
                    m += half + 1;
                    half -= ( len & 1 ) ^ 1;
                }
            }

            if ( 0 < m )
            {
                lm = middle - m;
                rm = middle + m;
                ss_blockswap( SA, lm, middle, m );
                l = r = middle;
                next = 0;
                if ( rm < last )
                {
                    if ( SA[rm] < 0 )
                    {
                        SA[rm] = ~SA[rm];
                        if ( first < lm )
                        {
                            for ( ; SA[--l] < 0; )
                            {
                            }
                            next |= 4;
                        }
                        next |= 1;
                    }
                    else if ( first < lm )
                    {
                        for ( ; SA[r] < 0; ++r )
                        {
                        }
                        next |= 2;
                    }
                }

                if ( ( l - first ) <= ( last - r ) )
                {
                    ssize = STACK_PUSH( stack, ssize, r, rm, last, ( next & 3 ) | ( check & 4 ) );
                    middle = lm;
                    last = l;
                    check = ( check & 3 ) | ( next & 4 );
                }
                else
                {
                    if ( ( next & 2 ) != 0 && ( r == middle ) )
                    {
                        next ^= 6;
                    }
                    ssize = STACK_PUSH( stack, ssize, first, lm, l, ( check & 3 ) | ( next & 4 ) );
                    first = r;
                    middle = rm;
                    check = ( next & 3 ) | ( check & 4 );
                }
            }
            else
            {
                if ( ss_compare( T, SA, xpa + GETIDX( SA[middle - 1] ), xpa + SA[middle], depth ) == 0 )
                {
                    SA[middle] = ~SA[middle];
                }
                MERGE_CHECK( T, SA, xpa, depth, first, last, check );
                // STACK_POP(first, middle, last, check)
                if ( ssize == 0 )
                    return;
                first = stack[ssize - 4];
                middle = stack[ssize - 3];
                last = stack[ssize - 2];
                check = stack[ssize - 1];
                ssize -= 4;
            }
        }
    }

    /*---------------------------------------------------------------------------*/

    /*- Function -*/

    /* Substring sort */
    final void sssort( final byte[] T, final int[] SA, final int xpa, int first, final int last, int buf, int bufsize,
                       final int depth, final int n, final boolean lastsuffix )
    {
        int a;
        int b, middle, curbuf;
        int j, k, curbufsize, limit;
        int i;

        if ( lastsuffix )
        {
            ++first;
        }

        if ( ( bufsize < SS_BLOCKSIZE ) && ( bufsize < ( last - first ) )
            && ( bufsize < ( limit = isqrt( last - first, SS_BLOCKSIZE ) ) ) )
        {
            if ( SS_BLOCKSIZE < limit )
            {
                limit = SS_BLOCKSIZE;
            }
            buf = middle = last - limit;
            bufsize = limit;
        }
        else
        {
            middle = last;
            limit = 0;
        }
        for ( a = first, i = 0; SS_BLOCKSIZE < ( middle - a ); a += SS_BLOCKSIZE, ++i )
        {
            ss_mintrosort( T, SA, xpa, a, a + SS_BLOCKSIZE, depth );
            curbufsize = last - ( a + SS_BLOCKSIZE );
            curbuf = a + SS_BLOCKSIZE;
            if ( curbufsize <= bufsize )
            {
                curbufsize = bufsize;
                curbuf = buf;
            }
            for ( b = a, k = SS_BLOCKSIZE, j = i; ( j & 1 ) != 0; b -= k, k <<= 1, j >>= 1 )
            {
                ss_swapmerge( T, SA, xpa, b - k, b, b + k, curbuf, curbufsize, depth );
            }
        }
        ss_mintrosort( T, SA, xpa, a, middle, depth );
        for ( k = SS_BLOCKSIZE; i != 0; k <<= 1, i >>= 1 )
        {
            if ( ( i & 1 ) != 0 )
            {
                ss_swapmerge( T, SA, xpa, a - k, a, middle, buf, bufsize, depth );
                a -= k;
            }
        }
        if ( limit != 0 )
        {
            ss_mintrosort( T, SA, xpa, middle, last, depth );
            ss_inplacemerge( T, SA, xpa, first, middle, last, depth );
        }

        if ( lastsuffix )
        {
            /* Insert last type B* suffix. */
            int r;
            for ( a = first, i = SA[first - 1], r = 1; ( a < last )
                && ( ( SA[a] < 0 ) || ( 0 < ( r = ss_compare_last( T, SA, xpa, xpa + i, xpa + SA[a], depth, n ) ) ) ); ++a )
            {
                SA[a - 1] = SA[a];
            }
            if ( r == 0 )
            {
                SA[a] = ~SA[a];
            }
            SA[a - 1] = i;
        }
    }
}
