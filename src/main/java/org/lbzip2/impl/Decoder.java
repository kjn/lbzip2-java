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
import static org.lbzip2.impl.Constants.rand_table;

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
}
