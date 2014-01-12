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

import static org.lbzip2.impl.Constants.RAND_THRESH;
import static org.lbzip2.impl.Constants.crc_table;
import static org.lbzip2.impl.Constants.rand_table;
import static org.lbzip2.impl.Status.MORE;
import static org.lbzip2.impl.Status.OK;

import org.lbzip2.StreamFormatException;

class Decoder
{
    boolean rand; /* block randomized */

    int bwt_idx; /* BWT primary index */

    int block_size; /* compressed block size */

    int crc; /* expected block CRC */

    int[] ftab = new int[256]; /* frequency table used in counting sort */

    int[] tt;

    int rle_state; /* FSA state */

    int rle_crc; /* CRC checksum */

    int rle_index; /* IBWT linked list pointer */

    int rle_avail; /* available input bytes */

    int rle_char; /* current character */

    int rle_prev; /* prevoius character */

    void decode()
    {
        int i, j = 0;
        int cum;
        int uc;

        /* Transform counts into indices (cumulative counts). */
        cum = 0;
        for ( i = 0; i < 256; i++ )
            ftab[i] = ( cum += ftab[i] ) - ftab[i];
        assert ( cum == block_size );

        /**
         * Construct the IBWT singly-linked cyclic list. Traversing that list starting at primary index produces the
         * source string.
         * <p>
         * Each list node consists of a pointer to the next node and a character of the source string. Those 2 values
         * are packed into a single 32bit integer. The character is kept in bits 0-7 and the pointer in stored in bits
         * 8-27. Bits 28-31 are unused (always clear).
         * <p>
         * Note: Iff the source string consists of a string repeated k times (eg. ABABAB - the string AB is repeated k=3
         * times) then this algorithm will construct k independent (not connected), isomorphic lists.
         */
        for ( i = 0; i < block_size; i++ )
        {
            uc = tt[i];
            tt[ftab[uc]] += ( i << 8 );
            ftab[uc]++;
        }
        assert ( ftab[255] == block_size );

        /**
         * Derandomize the block if necessary.
         * <p>
         * The derandomization algorithm is implemented inefficiently, but the assumption is that randomized blocks are
         * unlikely to be encountered. Most of bzip2 implementations try to avoid randomizing blocks because it usually
         * leads to decreased compression ratio.
         */
        if ( rand )
        {
            byte[] block;

            /* Allocate a temporary array to hold the block. */
            block = new byte[block_size];

            /* Copy the IBWT linked list into the temporary array. */
            j = tt[bwt_idx];
            for ( i = 0; i < block_size; i++ )
            {
                j = tt[j >> 8];
                block[i] = (byte) j;
            }

            /* Derandomize the block. */
            i = 0;
            j = RAND_THRESH;
            while ( j < block_size )
            {
                block[j] ^= 1;
                i = ( i + 1 ) & 0x1FF;
                j += rand_table[i];
            }

            /* Reform a linked list from the array. */
            for ( i = 0; i < block_size; i++ )
                tt[i] = ( ( i + 1 ) << 8 ) + block[i];
        }

        rle_state = 0;
        rle_crc = -1;
        rle_index = rand ? 0 : tt[bwt_idx];
        rle_avail = block_size;
        rle_prev = 0;
        rle_char = 0;
    }

    /**
     * Emit decoded block into buffer buf of size *buf_sz. Buffer size is updated to reflect the remaining space left in
     * the buffer.
     * <p>
     * Returns OK if the block was completely emitted, MORE if more output space is needed to fully emit the block or
     * ERR_RUNLEN if data error was detected (missing run length).
     * 
     * @throws StreamFormatException
     */
    Status emit( byte[] buf, int off, int[] buf_sz )
        throws StreamFormatException
    {
        int p; /* IBWT linked list pointer */
        int a; /* available input bytes */
        int s; /* CRC checksum */
        int c; /* current character */
        int d; /* next character */
        int[] t; /* IBWT linked list base address */
        int b; /* next free byte in output buffer */
        int m; /* number of free output bytes available */

        t = tt;
        b = off;
        m = buf_sz[0];

        s = rle_crc;
        p = rle_index;
        a = rle_avail;
        c = rle_char;
        d = rle_prev;

        /* === UNRLE FINITE STATE AUTOMATON === */
        /* There are 6 states, numbered from 0 to 5. */

        /*
         * Excuse me, but the following is a write-only code. It wasn't written for readability or maintainability, but
         * rather for high efficiency.
         */
        switch ( rle_state )
        {
            default:
                throw new IllegalStateException();
            case 1:
                if ( m-- == 0 )
                    break;
                s = ( s << 8 ) ^ crc_table[( s >> 24 ) ^ ( buf[b++] = (byte) c )];
                if ( c != d )
                    break;
                if ( a-- == 0 )
                    break;
                p = t[p >> 8];
                c = p & 0xFF;
            case 2:
                if ( m-- == 0 )
                {
                    rle_state = 2;
                    break;
                }
                s = ( s << 8 ) ^ crc_table[( s >> 24 ) ^ ( buf[b++] = (byte) c )];
                if ( c != d )
                    break;
                if ( a-- == 0 )
                    break;
                p = t[p >> 8];
                c = p & 0xFF;
            case 3:
                if ( m-- == 0 )
                {
                    rle_state = 3;
                    break;
                }
                s = ( s << 8 ) ^ crc_table[( s >> 24 ) ^ ( buf[b++] = (byte) c )];
                if ( c != d )
                    break;
                if ( a-- == 0 )
                    throw new StreamFormatException( "ERR_RUNLEN" );
                p = t[p >> 8];
                c = p & 0xFF;
            case 4:
                if ( m < c )
                {
                    c -= m;
                    while ( m-- != 0 )
                        s = ( s << 8 ) ^ crc_table[( s >> 24 ) ^ ( buf[b++] = (byte) d )];
                    rle_state = 4;
                    break;
                }
                m -= c;
                while ( c-- != 0 )
                    s = ( s << 8 ) ^ crc_table[( s >> 24 ) ^ ( buf[b++] = (byte) d )];
            case 0:
                if ( a-- == 0 )
                    break;
                p = t[p >> 8];
                c = p & 0xFF;
            case 5:
                if ( m-- == 0 )
                {
                    rle_state = 5;
                    break;
                }
                s = ( s << 8 ) ^ crc_table[( s >> 24 ) ^ ( buf[b++] = (byte) c )];
        }

        if ( a != -1 && m != -1 )
        {
            for ( ;; )
            {
                if ( a-- == 0 )
                    break;
                d = c;
                p = t[p >> 8];
                c = p & 0xFF;
                if ( m-- == 0 )
                {
                    rle_state = 1;
                    break;
                }
                s = ( s << 8 ) ^ crc_table[( s >> 24 ) ^ ( buf[b++] = (byte) c )];
                if ( c != d )
                {
                    if ( a-- == 0 )
                        break;
                    d = c;
                    p = t[p >> 8];
                    c = p & 0xFF;
                    if ( m-- == 0 )
                    {
                        rle_state = 1;
                        break;
                    }
                    s = ( s << 8 ) ^ crc_table[( s >> 24 ) ^ ( buf[b++] = (byte) c )];
                    if ( c != d )
                    {
                        if ( a-- == 0 )
                            break;
                        d = c;
                        p = t[p >> 8];
                        c = p & 0xFF;
                        if ( m-- == 0 )
                        {
                            rle_state = 1;
                            break;
                        }
                        s = ( s << 8 ) ^ crc_table[( s >> 24 ) ^ ( buf[b++] = (byte) c )];
                        if ( c != d )
                        {
                            if ( a-- == 0 )
                                break;
                            d = c;
                            p = t[p >> 8];
                            c = p & 0xFF;
                            if ( m-- == 0 )
                            {
                                rle_state = 1;
                                break;
                            }
                            s = ( s << 8 ) ^ crc_table[( s >> 24 ) ^ ( buf[b++] = (byte) c )];
                            if ( c != d )
                                continue;
                        }
                    }
                }
                if ( a-- == 0 )
                    break;
                p = t[p >> 8];
                c = p & 0xFF;
                if ( m-- == 0 )
                {
                    rle_state = 2;
                    break;
                }
                s = ( s << 8 ) ^ crc_table[( s >> 24 ) ^ ( buf[b++] = (byte) c )];
                if ( c != d )
                    continue;
                if ( a-- == 0 )
                    break;
                p = t[p >> 8];
                c = p & 0xFF;
                if ( m-- == 0 )
                {
                    rle_state = 3;
                    break;
                }
                s = ( s << 8 ) ^ crc_table[( s >> 24 ) ^ ( buf[b++] = (byte) c )];
                if ( c != d )
                    continue;
                if ( a-- == 0 )
                    throw new StreamFormatException( "ERR_RUNLEN" );
                p = t[p >> 8];
                c = p & 0xFF;
                if ( m < c )
                {
                    c -= m;
                    while ( m-- != 0 )
                        s = ( s << 8 ) ^ crc_table[( s >> 24 ) ^ ( buf[b++] = (byte) d )];
                    rle_state = 4;
                    break;
                }
                m -= c;
                while ( c-- != 0 )
                    s = ( s << 8 ) ^ crc_table[( s >> 24 ) ^ ( buf[b++] = (byte) d )];
                if ( a-- == 0 )
                    break;
                p = t[p >> 8];
                c = p & 0xFF;
                if ( m-- == 0 )
                {
                    rle_state = 5;
                    break;
                }
                s = ( s << 8 ) ^ crc_table[( s >> 24 ) ^ ( buf[b++] = (byte) c )];
            }
        }

        /* Exactly one of `a' and `m' is equal to M1. */
        assert ( ( a == -1 ) != ( m == -1 ) );

        rle_avail = a;
        if ( m == -1 )
        {
            assert ( a != -1 );
            rle_index = p;
            rle_char = c;
            rle_prev = d;
            rle_crc = s;
            buf_sz[0] = 0;
            return MORE;
        }

        assert ( a == -1 );
        crc = s ^ -1;
        buf_sz[0] = m;
        return OK;
    }
}
