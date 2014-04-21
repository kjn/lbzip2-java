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
package org.lbzip2.impl;

import static org.lbzip2.impl.Utils.ilog2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Mikolaj Izdebski
 */
final class TandemRepeatSort
{
    private final Logger logger = LoggerFactory.getLogger( TandemRepeatSort.class );

    /* for trsort.c */
    private static final int TR_INSERTIONSORT_THRESHOLD = 8;

    private static final int TR_STACKSIZE = 64;

    private final int STACK_PUSH5( final int[] stack, final int ssize, final int a, final int b, final int c,
                                   final int d, final int e )
    {
        stack[ssize] = a;
        stack[ssize + 1] = b;
        stack[ssize + 2] = c;
        stack[ssize + 3] = d;
        stack[ssize + 4] = e;
        return ssize + 5;
    }

    /* for trsort.c */
    private final int TR_GETC( final int[] SA, final int depth, final int num_bstar, final int p )
    {
        return ( p < ( num_bstar - depth ) ) ? SA[num_bstar + p + depth] : SA[num_bstar + ( p + depth ) % num_bstar];
    }

    /*---- trsort ----*/

    /*- Private Functions -*/

    /*---------------------------------------------------------------------------*/

    /* Simple insertionsort for small size groups. */
    private final void tr_insertionsort( final int[] SA, final int depth, final int num_bstar, final int first,
                                         final int last )
    {
        int a, b;
        int t, r;

        for ( a = first + 1; a < last; ++a )
        {
            for ( t = SA[a], b = a - 1; 0 > ( r =
                TR_GETC( SA, depth, num_bstar, t ) - TR_GETC( SA, depth, num_bstar, SA[b] ) ); )
            {
                do
                {
                    SA[b + 1] = SA[b];
                }
                while ( ( first <= --b ) && ( SA[b] < 0 ) );
                if ( b < first )
                {
                    break;
                }
            }
            if ( r == 0 )
            {
                SA[b] = ~SA[b];
            }
            SA[b + 1] = t;
        }
    }

    /*---------------------------------------------------------------------------*/

    private final void tr_fixdown( final int[] SA, final int depth, final int num_bstar, final int root, int i,
                                   final int size )
    {
        int j, k;
        int v;
        int c, d, e;

        for ( v = SA[root + i], c = TR_GETC( SA, depth, num_bstar, v ); ( j = 2 * i + 1 ) < size; SA[root + i] =
            SA[root + k], i = k )
        {
            k = j++;
            d = TR_GETC( SA, depth, num_bstar, SA[root + k] );
            if ( d < ( e = TR_GETC( SA, depth, num_bstar, SA[root + j] ) ) )
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
    private final void tr_heapsort( final int[] SA, final int depth, final int num_bstar, final int root, final int size )
    {
        int i, m;
        int t;

        m = size;
        if ( ( size % 2 ) == 0 )
        {
            m--;
            if ( TR_GETC( SA, depth, num_bstar, SA[root + m / 2] ) < TR_GETC( SA, depth, num_bstar, SA[root + m] ) )
            {
                t = SA[root + m];
                SA[root + m] = SA[root + m / 2];
                SA[root + m / 2] = t;
            }
        }

        for ( i = m / 2 - 1; 0 <= i; --i )
        {
            tr_fixdown( SA, depth, num_bstar, root, i, m );
        }
        if ( ( size % 2 ) == 0 )
        {
            t = SA[root];
            SA[root] = SA[root + m];
            SA[root + m] = t;
            tr_fixdown( SA, depth, num_bstar, root, 0, m );
        }
        for ( i = m - 1; 0 < i; --i )
        {
            t = SA[root];
            SA[root] = SA[root + i];
            tr_fixdown( SA, depth, num_bstar, root, 0, i );
            SA[root + i] = t;
        }
    }

    /*---------------------------------------------------------------------------*/

    /* Returns the median of three elements. */
    private final int tr_median3( final int[] SA, final int depth, final int num_bstar, int v1, int v2, final int v3 )
    {
        int t;
        if ( TR_GETC( SA, depth, num_bstar, SA[v1] ) > TR_GETC( SA, depth, num_bstar, SA[v2] ) )
        {
            t = v1;
            v1 = v2;
            v2 = t;
        }
        if ( TR_GETC( SA, depth, num_bstar, SA[v2] ) > TR_GETC( SA, depth, num_bstar, SA[v3] ) )
        {
            if ( TR_GETC( SA, depth, num_bstar, SA[v1] ) > TR_GETC( SA, depth, num_bstar, SA[v3] ) )
            {
                return v1;
            }
            else
            {
                return v3;
            }
        }
        return v2;
    }

    /* Returns the median of five elements. */
    private final int tr_median5( final int[] SA, final int depth, final int num_bstar, int v1, int v2, int v3, int v4,
                                  int v5 )
    {
        int t;
        if ( TR_GETC( SA, depth, num_bstar, SA[v2] ) > TR_GETC( SA, depth, num_bstar, SA[v3] ) )
        {
            t = v2;
            v2 = v3;
            v3 = t;
        }
        if ( TR_GETC( SA, depth, num_bstar, SA[v4] ) > TR_GETC( SA, depth, num_bstar, SA[v5] ) )
        {
            t = v4;
            v4 = v5;
            v5 = t;
        }
        if ( TR_GETC( SA, depth, num_bstar, SA[v2] ) > TR_GETC( SA, depth, num_bstar, SA[v4] ) )
        {
            t = v2;
            v2 = v4;
            v4 = t;
            t = v3;
            v3 = v5;
            v5 = t;
        }
        if ( TR_GETC( SA, depth, num_bstar, SA[v1] ) > TR_GETC( SA, depth, num_bstar, SA[v3] ) )
        {
            t = v1;
            v1 = v3;
            v3 = t;
        }
        if ( TR_GETC( SA, depth, num_bstar, SA[v1] ) > TR_GETC( SA, depth, num_bstar, SA[v4] ) )
        {
            t = v1;
            v1 = v4;
            v4 = t;
            t = v3;
            v3 = v5;
            v5 = t;
        }
        if ( TR_GETC( SA, depth, num_bstar, SA[v3] ) > TR_GETC( SA, depth, num_bstar, SA[v4] ) )
        {
            return v4;
        }
        return v3;
    }

    /* Returns the pivot element. */
    private final int tr_pivot( final int[] SA, final int depth, final int num_bstar, int first, int last )
    {
        int middle;
        int t;

        t = last - first;
        middle = first + t / 2;

        if ( t <= 512 )
        {
            if ( t <= 32 )
            {
                return tr_median3( SA, depth, num_bstar, first, middle, last - 1 );
            }
            else
            {
                t >>= 2;
                return tr_median5( SA, depth, num_bstar, first, first + t, middle, last - 1 - t, last - 1 );
            }
        }
        t >>= 3;
        first = tr_median3( SA, depth, num_bstar, first, first + t, first + ( t << 1 ) );
        middle = tr_median3( SA, depth, num_bstar, middle - t, middle, middle + t );
        last = tr_median3( SA, depth, num_bstar, last - 1 - ( t << 1 ), last - 1 - t, last - 1 );
        return tr_median3( SA, depth, num_bstar, first, middle, last );
    }

    /*---------------------------------------------------------------------------*/

    int chance;

    int remain;

    int incval;

    int count;

    private final void trbudget_init( final int chance, final int incval )
    {
        this.chance = chance;
        this.remain = this.incval = incval;
    }

    private final boolean trbudget_check( final int size )
    {
        if ( size <= this.remain )
        {
            this.remain -= size;
            return true;
        }
        if ( this.chance == 0 )
        {
            this.count += size;
            return false;
        }
        this.remain += this.incval - size;
        this.chance -= 1;
        return true;
    }

    /*---------------------------------------------------------------------------*/

    private final long tr_partition( final int[] SA, final int depth, final int num_bstar, int first, final int middle,
                                     int last, final int v )
    {
        int a, b, c, d, e, f;
        int t, s;
        int x = 0;

        for ( b = middle - 1; ( ++b < last ) && ( ( x = TR_GETC( SA, depth, num_bstar, SA[b] ) ) == v ); )
        {
        }
        if ( ( ( a = b ) < last ) && ( x < v ) )
        {
            for ( ; ( ++b < last ) && ( ( x = TR_GETC( SA, depth, num_bstar, SA[b] ) ) <= v ); )
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
        for ( c = last; ( b < --c ) && ( ( x = TR_GETC( SA, depth, num_bstar, SA[c] ) ) == v ); )
        {
        }
        if ( ( b < ( d = c ) ) && ( x > v ) )
        {
            for ( ; ( b < --c ) && ( ( x = TR_GETC( SA, depth, num_bstar, SA[c] ) ) >= v ); )
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
            for ( ; ( ++b < c ) && ( ( x = TR_GETC( SA, depth, num_bstar, SA[b] ) ) <= v ); )
            {
                if ( x == v )
                {
                    t = SA[b];
                    SA[b] = SA[a];
                    SA[a] = t;
                    ++a;
                }
            }
            for ( ; ( b < --c ) && ( ( x = TR_GETC( SA, depth, num_bstar, SA[c] ) ) >= v ); )
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
            first += ( b - a );
            last -= ( d - c );
        }
        return ( (long) first << 32 ) + last;
    }

    private final void tr_copy( final int[] SA, final int num_bstar, final int first, final int a, final int b,
                                final int last, final int depth )
    {
        /*
         * sort suffixes of middle partition by using sorted order of suffixes of left and right partition.
         */
        int c, d, e;
        int s, v;

        v = b - 1;
        for ( c = first, d = a - 1; c <= d; ++c )
        {
            if ( ( s = SA[c] - depth ) < 0 )
            {
                s += num_bstar;
            }
            if ( SA[num_bstar + s] == v )
            {
                SA[++d] = s;
                SA[num_bstar + s] = d;
            }
        }
        for ( c = last - 1, e = d + 1, d = b; e < d; --c )
        {
            if ( ( s = SA[c] - depth ) < 0 )
            {
                s += num_bstar;
            }
            if ( SA[num_bstar + s] == v )
            {
                SA[--d] = s;
                SA[num_bstar + s] = d;
            }
        }
    }

    private final void tr_partialcopy( final int[] SA, final int num_bstar, final int first, final int a, final int b,
                                       final int last, final int depth )
    {
        int c, d, e;
        int s, v, t;
        int rank, lastrank, newrank = -1;

        v = b - 1;
        lastrank = -1;
        for ( c = first, d = a - 1; c <= d; ++c )
        {
            t = SA[c];
            if ( ( s = t - depth ) < 0 )
            {
                s += num_bstar;
            }
            if ( SA[num_bstar + s] == v )
            {
                SA[++d] = s;
                rank = SA[num_bstar + t];
                if ( lastrank != rank )
                {
                    lastrank = rank;
                    newrank = d;
                }
                SA[num_bstar + s] = newrank;
            }
        }

        lastrank = -1;
        for ( e = d; first <= e; --e )
        {
            rank = SA[num_bstar + SA[e]];
            if ( lastrank != rank )
            {
                lastrank = rank;
                newrank = e;
            }
            if ( newrank != rank )
            {
                SA[num_bstar + SA[e]] = newrank;
            }
        }

        lastrank = -1;
        for ( c = last - 1, e = d + 1, d = b; e < d; --c )
        {
            t = SA[c];
            if ( ( s = t - depth ) < 0 )
            {
                s += num_bstar;
            }
            if ( SA[num_bstar + s] == v )
            {
                SA[--d] = s;
                rank = SA[num_bstar + t];
                if ( lastrank != rank )
                {
                    lastrank = rank;
                    newrank = d;
                }
                SA[num_bstar + s] = newrank;
            }
        }
    }

    private final void tr_introsort( final int[] SA, int depth, final int num_bstar, int first, int last )
    {
        final int[] stack = new int[5 * TR_STACKSIZE];
        int a, b, c;
        int t;
        int v, x = 0;
        final int incr = depth;
        int limit, next;
        int ssize, trlink = -1;
        long range;

        for ( ssize = 0, limit = ilog2( last - first );; )
        {
            assert ( ( depth < num_bstar ) || ( limit == -3 ) );

            if ( limit < 0 )
            {
                if ( limit == -1 )
                {
                    /* tandem repeat partition */
                    range = tr_partition( SA, depth - incr, num_bstar, first, first, last, last - 1 );
                    a = (int) ( range >> 32 );
                    b = (int) range;

                    /* update ranks */
                    if ( a < last )
                    {
                        for ( c = first, v = a - 1; c < a; ++c )
                        {
                            SA[num_bstar + SA[c]] = v;
                        }
                    }
                    if ( b < last )
                    {
                        for ( c = a, v = b - 1; c < b; ++c )
                        {
                            SA[num_bstar + SA[c]] = v;
                        }
                    }

                    /* push */
                    if ( 1 < ( b - a ) )
                    {
                        ssize = STACK_PUSH5( stack, ssize, Integer.MIN_VALUE, a, b, 0, 0 );
                        ssize = STACK_PUSH5( stack, ssize, depth - incr, first, last, -2, trlink );
                        trlink = ssize - 10;
                    }
                    if ( ( a - first ) <= ( last - b ) )
                    {
                        if ( 1 < ( a - first ) )
                        {
                            ssize = STACK_PUSH5( stack, ssize, depth, b, last, ilog2( last - b ), trlink );
                            last = a;
                            limit = ilog2( a - first );
                        }
                        else if ( 1 < ( last - b ) )
                        {
                            first = b;
                            limit = ilog2( last - b );
                        }
                        else
                        {
                            // STACK_POP5(depth, first, last, limit, trlink)
                            if ( ssize == 0 )
                                return;
                            depth = stack[ssize - 5];
                            first = stack[ssize - 4];
                            last = stack[ssize - 3];
                            limit = stack[ssize - 2];
                            trlink = stack[ssize - 1];
                            ssize -= 5;
                        }
                    }
                    else
                    {
                        if ( 1 < ( last - b ) )
                        {
                            ssize = STACK_PUSH5( stack, ssize, depth, first, a, ilog2( a - first ), trlink );
                            first = b;
                            limit = ilog2( last - b );
                        }
                        else if ( 1 < ( a - first ) )
                        {
                            last = a;
                            limit = ilog2( a - first );
                        }
                        else
                        {
                            // STACK_POP5(depth, first, last, limit, trlink)
                            if ( ssize == 0 )
                                return;
                            depth = stack[ssize - 5];
                            first = stack[ssize - 4];
                            last = stack[ssize - 3];
                            limit = stack[ssize - 2];
                            trlink = stack[ssize - 1];
                            ssize -= 5;
                        }
                    }
                }
                else if ( limit == -2 )
                {
                    /* tandem repeat copy */
                    // STACK_POP5(_, a, b, limit, _)
                    if ( ssize == 0 )
                        return;
                    a = stack[ssize - 4];
                    b = stack[ssize - 3];
                    limit = stack[ssize - 2];
                    ssize -= 5;
                    if ( limit == 0 )
                    {
                        tr_copy( SA, num_bstar, first, a, b, last, depth );
                    }
                    else
                    {
                        if ( 0 <= trlink )
                        {
                            stack[trlink + 3] = -1;
                        }
                        tr_partialcopy( SA, num_bstar, first, a, b, last, depth );
                    }
                    // STACK_POP5(depth, first, last, limit, trlink)
                    if ( ssize == 0 )
                        return;
                    depth = stack[ssize - 5];
                    first = stack[ssize - 4];
                    last = stack[ssize - 3];
                    limit = stack[ssize - 2];
                    trlink = stack[ssize - 1];
                    ssize -= 5;
                }
                else
                {
                    /* sorted partition */
                    if ( 0 <= SA[first] )
                    {
                        a = first;
                        do
                        {
                            SA[num_bstar + SA[a]] = a;
                        }
                        while ( ( ++a < last ) && ( 0 <= SA[a] ) );
                        first = a;
                    }
                    if ( first < last )
                    {
                        a = first;
                        do
                        {
                            SA[a] = ~SA[a];
                        }
                        while ( SA[++a] < 0 );
                        next =
                            ( incr < ( num_bstar - depth ) ) ? ( ( SA[num_bstar + SA[a]] != TR_GETC( SA, depth,
                                                                                                     num_bstar, SA[a] ) ) ? ilog2( a
                                - first + 1 )
                                            : -1 )
                                            : -3;
                        if ( ++a < last )
                        {
                            for ( b = first, v = a - 1; b < a; ++b )
                            {
                                SA[num_bstar + SA[b]] = v;
                            }
                        }

                        /* push */
                        if ( trbudget_check( a - first ) )
                        {
                            if ( ( a - first ) <= ( last - a ) )
                            {
                                ssize = STACK_PUSH5( stack, ssize, depth, a, last, -3, trlink );
                                depth += incr;
                                last = a;
                                limit = next;
                            }
                            else
                            {
                                if ( 1 < ( last - a ) )
                                {
                                    ssize = STACK_PUSH5( stack, ssize, depth + incr, first, a, next, trlink );
                                    first = a;
                                    limit = -3;
                                }
                                else
                                {
                                    depth += incr;
                                    last = a;
                                    limit = next;
                                }
                            }
                        }
                        else
                        {
                            if ( 0 <= trlink )
                            {
                                stack[trlink + 3] = -1;
                            }
                            if ( 1 < ( last - a ) )
                            {
                                first = a;
                                limit = -3;
                            }
                            else
                            {
                                // STACK_POP5(depth, first, last, limit, trlink)
                                if ( ssize == 0 )
                                    return;
                                depth = stack[ssize - 5];
                                first = stack[ssize - 4];
                                last = stack[ssize - 3];
                                limit = stack[ssize - 2];
                                trlink = stack[ssize - 1];
                                ssize -= 5;
                            }
                        }
                    }
                    else
                    {
                        // STACK_POP5(depth, first, last, limit, trlink)
                        if ( ssize == 0 )
                            return;
                        depth = stack[ssize - 5];
                        first = stack[ssize - 4];
                        last = stack[ssize - 3];
                        limit = stack[ssize - 2];
                        trlink = stack[ssize - 1];
                        ssize -= 5;
                    }
                }
                continue;
            }

            if ( ( last - first ) <= TR_INSERTIONSORT_THRESHOLD )
            {
                tr_insertionsort( SA, depth, num_bstar, first, last );
                limit = -3;
                continue;
            }

            if ( limit-- == 0 )
            {
                tr_heapsort( SA, depth, num_bstar, first, last - first );
                for ( a = last - 1; first < a; a = b )
                {
                    for ( x = TR_GETC( SA, depth, num_bstar, SA[a] ), b = a - 1; ( first <= b )
                        && ( TR_GETC( SA, depth, num_bstar, SA[b] ) == x ); --b )
                    {
                        SA[b] = ~SA[b];
                    }
                }
                limit = -3;
                continue;
            }

            /* choose pivot */
            a = tr_pivot( SA, depth, num_bstar, first, last );
            t = SA[first];
            SA[first] = SA[a];
            SA[a] = t;
            v = TR_GETC( SA, depth, num_bstar, SA[first] );

            /* partition */
            range = tr_partition( SA, depth, num_bstar, first, first + 1, last, v );
            a = (int) ( range >> 32 );
            b = (int) range;
            if ( ( last - first ) != ( b - a ) )
            {
                next = ( incr < ( num_bstar - depth ) ) ? ( ( SA[num_bstar + SA[a]] != v ) ? ilog2( b - a ) : -1 ) : -3;

                /* update ranks */
                for ( c = first, v = a - 1; c < a; ++c )
                {
                    SA[num_bstar + SA[c]] = v;
                }
                if ( b < last )
                {
                    for ( c = a, v = b - 1; c < b; ++c )
                    {
                        SA[num_bstar + SA[c]] = v;
                    }
                }

                /* push */
                if ( ( 1 < ( b - a ) ) && ( trbudget_check( b - a ) ) )
                {
                    if ( ( a - first ) <= ( last - b ) )
                    {
                        if ( ( last - b ) <= ( b - a ) )
                        {
                            if ( 1 < ( a - first ) )
                            {
                                ssize = STACK_PUSH5( stack, ssize, depth + incr, a, b, next, trlink );
                                ssize = STACK_PUSH5( stack, ssize, depth, b, last, limit, trlink );
                                last = a;
                            }
                            else if ( 1 < ( last - b ) )
                            {
                                ssize = STACK_PUSH5( stack, ssize, depth + incr, a, b, next, trlink );
                                first = b;
                            }
                            else
                            {
                                depth += incr;
                                first = a;
                                last = b;
                                limit = next;
                            }
                        }
                        else if ( ( a - first ) <= ( b - a ) )
                        {
                            if ( 1 < ( a - first ) )
                            {
                                ssize = STACK_PUSH5( stack, ssize, depth, b, last, limit, trlink );
                                ssize = STACK_PUSH5( stack, ssize, depth + incr, a, b, next, trlink );
                                last = a;
                            }
                            else
                            {
                                ssize = STACK_PUSH5( stack, ssize, depth, b, last, limit, trlink );
                                depth += incr;
                                first = a;
                                last = b;
                                limit = next;
                            }
                        }
                        else
                        {
                            ssize = STACK_PUSH5( stack, ssize, depth, b, last, limit, trlink );
                            ssize = STACK_PUSH5( stack, ssize, depth, first, a, limit, trlink );
                            depth += incr;
                            first = a;
                            last = b;
                            limit = next;
                        }
                    }
                    else
                    {
                        if ( ( a - first ) <= ( b - a ) )
                        {
                            if ( 1 < ( last - b ) )
                            {
                                ssize = STACK_PUSH5( stack, ssize, depth + incr, a, b, next, trlink );
                                ssize = STACK_PUSH5( stack, ssize, depth, first, a, limit, trlink );
                                first = b;
                            }
                            else if ( 1 < ( a - first ) )
                            {
                                ssize = STACK_PUSH5( stack, ssize, depth + incr, a, b, next, trlink );
                                last = a;
                            }
                            else
                            {
                                depth += incr;
                                first = a;
                                last = b;
                                limit = next;
                            }
                        }
                        else if ( ( last - b ) <= ( b - a ) )
                        {
                            if ( 1 < ( last - b ) )
                            {
                                ssize = STACK_PUSH5( stack, ssize, depth, first, a, limit, trlink );
                                ssize = STACK_PUSH5( stack, ssize, depth + incr, a, b, next, trlink );
                                first = b;
                            }
                            else
                            {
                                ssize = STACK_PUSH5( stack, ssize, depth, first, a, limit, trlink );
                                depth += incr;
                                first = a;
                                last = b;
                                limit = next;
                            }
                        }
                        else
                        {
                            ssize = STACK_PUSH5( stack, ssize, depth, first, a, limit, trlink );
                            ssize = STACK_PUSH5( stack, ssize, depth, b, last, limit, trlink );
                            depth += incr;
                            first = a;
                            last = b;
                            limit = next;
                        }
                    }
                }
                else
                {
                    if ( ( 1 < ( b - a ) ) && ( 0 <= trlink ) )
                    {
                        stack[trlink + 3] = -1;
                    }
                    if ( ( a - first ) <= ( last - b ) )
                    {
                        if ( 1 < ( a - first ) )
                        {
                            ssize = STACK_PUSH5( stack, ssize, depth, b, last, limit, trlink );
                            last = a;
                        }
                        else if ( 1 < ( last - b ) )
                        {
                            first = b;
                        }
                        else
                        {
                            // STACK_POP5(depth, first, last, limit, trlink)
                            if ( ssize == 0 )
                                return;
                            depth = stack[ssize - 5];
                            first = stack[ssize - 4];
                            last = stack[ssize - 3];
                            limit = stack[ssize - 2];
                            trlink = stack[ssize - 1];
                            ssize -= 5;
                        }
                    }
                    else
                    {
                        if ( 1 < ( last - b ) )
                        {
                            ssize = STACK_PUSH5( stack, ssize, depth, first, a, limit, trlink );
                            first = b;
                        }
                        else if ( 1 < ( a - first ) )
                        {
                            last = a;
                        }
                        else
                        {
                            // STACK_POP5(depth, first, last, limit, trlink)
                            if ( ssize == 0 )
                                return;
                            depth = stack[ssize - 5];
                            first = stack[ssize - 4];
                            last = stack[ssize - 3];
                            limit = stack[ssize - 2];
                            trlink = stack[ssize - 1];
                            ssize -= 5;
                        }
                    }
                }
            }
            else
            {
                if ( trbudget_check( last - first ) )
                {
                    limit =
                        ( incr < ( num_bstar - depth ) ) ? ( ( SA[num_bstar + SA[first]] != TR_GETC( SA, depth,
                                                                                                     num_bstar,
                                                                                                     SA[first] ) ) ? ( limit + 1 )
                                        : -1 )
                                        : -3;
                    depth += incr;
                }
                else
                {
                    if ( 0 <= trlink )
                    {
                        stack[trlink + 3] = -1;
                    }
                    // STACK_POP5(depth, first, last, limit, trlink)
                    if ( ssize == 0 )
                        return;
                    depth = stack[ssize - 5];
                    first = stack[ssize - 4];
                    last = stack[ssize - 3];
                    limit = stack[ssize - 2];
                    trlink = stack[ssize - 1];
                    ssize -= 5;
                }
            }
        }
    }

    /*---------------------------------------------------------------------------*/

    /*- Function -*/

    /* Tandem repeat sort */
    final void trsort( final int[] SA, final int n, int depth )
    {
        int first, last, a;
        int t, skip, unsorted;

        if ( -n >= SA[0] )
        {
            return;
        }
        trbudget_init( ilog2( n ) * 2 / 3, n );
        for ( ;; depth += depth )
        {
            logger.trace( "    Tandem repeat sort at depth {}", depth );
            assert ( n > depth );
            first = 0;
            skip = 0;
            unsorted = 0;
            do
            {
                if ( ( t = SA[first] ) < 0 )
                {
                    first -= t;
                    skip += t;
                }
                else
                {
                    if ( skip != 0 )
                    {
                        SA[first + skip] = skip;
                        skip = 0;
                    }
                    last = SA[n + t] + 1;
                    if ( 1 < ( last - first ) )
                    {
                        this.count = 0;
                        tr_introsort( SA, depth, n, first, last );
                        if ( this.count != 0 )
                        {
                            unsorted += this.count;
                        }
                        else
                        {
                            skip = first - last;
                        }
                    }
                    else if ( ( last - first ) == 1 )
                    {
                        skip = -1;
                    }
                    first = last;
                }
            }
            while ( first < n );
            if ( skip != 0 )
            {
                SA[first + skip] = skip;
            }
            if ( unsorted == 0 || -n >= SA[0] )
            {
                break;
            }
            logger.trace( "      {} tandem repeats still remain unsorted", unsorted );
            if ( n <= ( depth ) * 2 )
            {
                do
                {
                    if ( ( t = SA[first] ) < 0 )
                    {
                        first -= t;
                    }
                    else
                    {
                        last = SA[n + t] + 1;
                        for ( a = first; a < last; ++a )
                        {
                            SA[n + SA[a]] = a;
                        }
                        first = last;
                    }
                }
                while ( first < n );
                break;
            }
        }

        logger.trace( "    Tandem repeat sort done" );
    }
}
