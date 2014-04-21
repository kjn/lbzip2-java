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

import static org.lbzip2.impl.Constants.MAX_RUN_LENGTH;
import static org.lbzip2.impl.Constants.crc_table;

import java.util.Arrays;

/**
 * @author Mikolaj Izdebski
 */
class Collector
{
    int nblock;

    final byte[] block;

    final int max_block_size;

    int block_crc;

    private int rle_state;

    private int rle_character;

    final boolean[] inuse = new boolean[256];

    public Collector( int max_block_size )
    {
        this.max_block_size = max_block_size;

        block = new byte[max_block_size + 1];
    }

    void reset()
    {
        Arrays.fill( inuse, false );
        rle_state = 0;
        block_crc = -1;
        nblock = 0;
    }

    boolean collect( byte[] inbuf, int off, int[] buf_sz )
    {
        /* Cache some often used member variables for faster access. */
        int avail = buf_sz[0];
        int p = off;
        int pLim = off + avail;
        int q = nblock;
        int qMax = max_block_size - 1;
        int ch, last;
        int run;
        int save_crc;
        int crc = block_crc;

        /*
         * State can't be equal to MAX_RUN_LENGTH because the run would have already been dumped by the previous
         * function call.
         */
        assert rle_state >= 0 && rle_state < MAX_RUN_LENGTH;

        main_loop: for ( ;; )
        {
            /* Finish any existing runs before starting a new one. */
            if ( rle_state != 0 )
            {
                ch = rle_character;

                finish_run: for ( ;; )
                {
                    /* There is an unfinished run from the previous call, try to finish it. */
                    if ( q >= qMax && ( q > qMax || ( rle_state == 3 && p < pLim && ( inbuf[p] & 0xFF ) == ch ) ) )
                    {
                        rle_state = -1;
                        break main_loop;
                    }

                    /* We have run out of input bytes before finishing the run. */
                    if ( p == pLim )
                        break main_loop;

                    /* If the run is at least 4 characters long, treat it specifically. */
                    if ( rle_state >= 4 )
                    {
                        /* Make sure we really have a long run. */
                        assert ( rle_state >= 4 );
                        assert ( q <= qMax );

                        while ( p < pLim )
                        {
                            /*
                             * Lookahead the next character. Terminate current run if lookahead character doesn't match.
                             */
                            if ( ( inbuf[p] & 0xFF ) != ch )
                            {
                                block[q++] = (byte) ( rle_state - 4 );
                                inuse[rle_state - 4] = true;
                                break finish_run;
                            }

                            /*
                             * Lookahead character turned out to be continuation of the run. Consume it and increase run
                             * length.
                             */
                            p++;
                            crc = ( crc << 8 ) ^ crc_table[( crc >>> 24 ) ^ ch];
                            rle_state++;

                            /*
                             * If the run has reached length of MAX_RUN_LENGTH, we have to terminate it prematurely
                             * (i.e. now).
                             */
                            if ( rle_state == MAX_RUN_LENGTH )
                            {
                                block[q++] = (byte) ( MAX_RUN_LENGTH - 4 );
                                inuse[MAX_RUN_LENGTH - 4] = true;
                                break finish_run;
                            }
                        }

                        /* We have ran out of input bytes before finishing the run. */
                        break main_loop;
                    }

                    /*
                     * Lookahead the next character. Terminate current run if lookahead character does not match.
                     */
                    if ( ( inbuf[p] & 0xFF ) != ch )
                        break finish_run;

                    /* Append the character to the run. */
                    p++;
                    crc = ( crc << 8 ) ^ crc_table[( crc >>> 24 ) ^ ch];
                    rle_state++;
                    block[q++] = (byte) ch;

                    /* We haven't finished the run yet, so keep going. */
                }
            }

            for ( ;; )
            {
                /* === STATE 0 === */
                if ( q > qMax )
                {
                    rle_state = -1;
                    break main_loop;
                }
                if ( p == pLim )
                {
                    rle_state = 0;
                    break main_loop;
                }
                ch = inbuf[p++] & 0xFF;
                crc = ( crc << 8 ) ^ crc_table[( crc >>> 24 ) ^ ch];

                state1: for ( ;; )
                {
                    for ( ;; )
                    {
                        /* === STATE 1 === */
                        inuse[ch] = true;
                        block[q++] = (byte) ch;
                        if ( q > qMax )
                        {
                            rle_state = -1;
                            break main_loop;
                        }
                        if ( p == pLim )
                        {
                            rle_state = 1;
                            rle_character = ch;
                            break main_loop;
                        }
                        last = ch;
                        ch = inbuf[p++] & 0xFF;
                        crc = ( crc << 8 ) ^ crc_table[( crc >>> 24 ) ^ ch];
                        if ( ch == last )
                            break;
                    }

                    /* === STATE 2 === */
                    block[q++] = (byte) ch;
                    if ( q > qMax )
                    {
                        rle_state = -1;
                        break main_loop;
                    }
                    if ( p == pLim )
                    {
                        rle_state = 2;
                        rle_character = ch;
                        break main_loop;
                    }
                    ch = inbuf[p++] & 0xFF;
                    crc = ( crc << 8 ) ^ crc_table[( crc >>> 24 ) ^ ch];
                    if ( ch != last )
                        continue;

                    /* === STATE 3 === */
                    block[q++] = (byte) ch;
                    if ( q >= qMax && ( q > qMax || ( p < pLim && ( inbuf[p] & 0xFF ) == last ) ) )
                    {
                        rle_state = -1;
                        break main_loop;
                    }
                    if ( p == pLim )
                    {
                        rle_state = 3;
                        rle_character = ch;
                        break main_loop;
                    }
                    ch = inbuf[p++] & 0xFF;
                    crc = ( crc << 8 ) ^ crc_table[( crc >>> 24 ) ^ ch];
                    if ( ch != last )
                        continue;

                    /* === STATE 4+ === */
                    assert q < qMax;
                    block[q++] = (byte) ch;

                    /*
                     * While the run is shorter than MAX_RUN_LENGTH characters, keep trying to append more characters to
                     * it.
                     */
                    for ( run = 4; run < MAX_RUN_LENGTH; run++ )
                    {
                        /* Check for end of input buffer. */
                        if ( p == pLim )
                        {
                            rle_state = run;
                            rle_character = ch;
                            break main_loop;
                        }

                        /* Fetch the next character. */
                        ch = inbuf[p++] & 0xFF;
                        save_crc = crc;
                        crc = ( crc << 8 ) ^ crc_table[( crc >>> 24 ) ^ ch];

                        /*
                         * If the character does not match, terminate the current run and start a fresh one.
                         */
                        if ( ch != last )
                        {
                            block[q++] = (byte) ( run - 4 );
                            inuse[run - 4] = true;
                            if ( q <= qMax )
                                continue state1;

                            /*
                             * There is no space left to begin a new run. Unget the last character and finish.
                             */
                            p--;
                            crc = save_crc;
                            rle_state = -1;
                            break main_loop;
                        }
                    }

                    /*
                     * The run has reached maximal length, so it must be ended prematurely.
                     */
                    block[q++] = (byte) ( MAX_RUN_LENGTH - 4 );
                    inuse[MAX_RUN_LENGTH - 4] = true;
                    break;
                }
            }
        }

        nblock = q;
        block_crc = crc;
        buf_sz[0] -= p - off;
        return rle_state < 0;
    }

    /* Finalize initial RLE. */
    void finish()
    {
        if ( rle_state >= 4 )
        {
            assert ( nblock < max_block_size );
            block[nblock++] = (byte) ( rle_state - 4 );
            inuse[rle_state - 4] = true;
        }
        assert ( nblock > 0 );
    }
}
