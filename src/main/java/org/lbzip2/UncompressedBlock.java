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
package org.lbzip2;

import static org.lbzip2.Constants.CHARACTER_BIAS;
import static org.lbzip2.Constants.MAX_BLOCK_SIZE;
import static org.lbzip2.Constants.MAX_RUN_LENGTH;
import static org.lbzip2.Constants.MIN_BLOCK_SIZE;
import static org.lbzip2.Constants.crc_table;

import java.util.Arrays;

/**
 * @author Mikolaj Izdebski
 */
public class UncompressedBlock
    extends AbstractDataSink
{
    int size;

    final byte[] block;

    private final int maxSize;

    int crc = -1;

    private int rleState;

    private int rleCharacter;

    final boolean[] inuse = new boolean[256];

    public UncompressedBlock()
    {
        this( MAX_BLOCK_SIZE );
    }

    public UncompressedBlock( int maxSize )
    {
        if ( maxSize < MIN_BLOCK_SIZE || maxSize > MAX_BLOCK_SIZE )
            throw new IllegalStateException( "Invalid maximal block size" );

        this.maxSize = maxSize;

        this.block = new byte[maxSize + 1];
    }

    /**
     * Check whether this block is empty.
     * 
     * @return {@code false} if there is some data pending in this block, {@code true} otherwise.
     */
    public boolean isEmpty()
    {
        return size == 0;
    }

    /**
     * Check whether this block is full.
     * 
     * @return {@code true} if no more data can be added to this block, {@code false} otherwise.
     */
    public boolean isFull()
    {
        return rleState < 0;
    }

    public int write( byte[] buf, int off, final int len )
    {
        if ( len == 0 )
            return 0;

        /* Cache some often used member variables for faster access. */
        final int maxOff = off + len;
        int size = this.size;
        int ch;
        int crc = this.crc;

        /*
         * State can't be equal to MAX_RUN_LENGTH because the run would have already been dumped by the previous
         * function call.
         */
        assert rleState >= 0 && rleState < MAX_RUN_LENGTH;

        main_loop: for ( ;; )
        {
            /* Finish any existing runs before starting a new one. */
            if ( rleState != 0 )
            {
                ch = rleCharacter;

                finish_run: for ( ;; )
                {
                    /* There is an unfinished run from the previous call, try to finish it. */
                    if ( size >= maxSize - 1
                        && ( size >= maxSize || ( rleState == 3 && off < maxOff && ( buf[off] & 0xFF ) == ch ) ) )
                    {
                        rleState = -1;
                        break main_loop;
                    }

                    /* We have run out of input bytes before finishing the run. */
                    if ( off == maxOff )
                        break main_loop;

                    /* If the run is at least 4 characters long, treat it specifically. */
                    if ( rleState >= 4 )
                    {
                        /* Make sure we really have a long run. */
                        assert rleState >= 4;
                        assert size < maxSize;

                        while ( off < maxOff )
                        {
                            /*
                             * Lookahead the next character. Terminate current run if lookahead character doesn't match.
                             */
                            if ( ( buf[off] & 0xFF ) != ch )
                            {
                                block[size++] = (byte) ( rleState - 4 + CHARACTER_BIAS );
                                inuse[rleState - 4] = true;
                                break finish_run;
                            }

                            /*
                             * Lookahead character turned out to be continuation of the run. Consume it and increase run
                             * length.
                             */
                            off++;
                            crc = ( crc << 8 ) ^ crc_table[( crc >>> 24 ) ^ ch];
                            rleState++;

                            /*
                             * If the run has reached length of MAX_RUN_LENGTH, we have to terminate it prematurely
                             * (i.e. now).
                             */
                            if ( rleState == MAX_RUN_LENGTH )
                            {
                                block[size++] = (byte) ( MAX_RUN_LENGTH - 4 + CHARACTER_BIAS );
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
                    if ( ( buf[off] & 0xFF ) != ch )
                        break finish_run;

                    /* Append the character to the run. */
                    off++;
                    crc = ( crc << 8 ) ^ crc_table[( crc >>> 24 ) ^ ch];
                    rleState++;
                    block[size++] = (byte) ( ch + CHARACTER_BIAS );

                    /* We haven't finished the run yet, so keep going. */
                }
            }

            for ( ;; )
            {
                /* === STATE 0 === */
                if ( size >= maxSize )
                {
                    rleState = -1;
                    break main_loop;
                }
                if ( off == maxOff )
                {
                    rleState = 0;
                    break main_loop;
                }
                ch = buf[off++] & 0xFF;
                crc = ( crc << 8 ) ^ crc_table[( crc >>> 24 ) ^ ch];

                state1: for ( ;; )
                {
                    int last;

                    do
                    {
                        /* === STATE 1 === */
                        inuse[ch] = true;
                        block[size++] = (byte) ( ch + CHARACTER_BIAS );
                        if ( size >= maxSize )
                        {
                            rleState = -1;
                            break main_loop;
                        }
                        if ( off == maxOff )
                        {
                            rleState = 1;
                            rleCharacter = ch;
                            break main_loop;
                        }
                        last = ch;
                        ch = buf[off++] & 0xFF;
                        crc = ( crc << 8 ) ^ crc_table[( crc >>> 24 ) ^ ch];
                    }
                    while ( ch != last );

                    /* === STATE 2 === */
                    block[size++] = (byte) ( ch + CHARACTER_BIAS );
                    if ( size >= maxSize )
                    {
                        rleState = -1;
                        break main_loop;
                    }
                    if ( off == maxOff )
                    {
                        rleState = 2;
                        rleCharacter = ch;
                        break main_loop;
                    }
                    ch = buf[off++] & 0xFF;
                    crc = ( crc << 8 ) ^ crc_table[( crc >>> 24 ) ^ ch];
                    if ( ch != last )
                        continue;

                    /* === STATE 3 === */
                    block[size++] = (byte) ( ch + CHARACTER_BIAS );
                    if ( size >= maxSize - 1 && ( size >= maxSize || ( off < maxOff && ( buf[off] & 0xFF ) == last ) ) )
                    {
                        rleState = -1;
                        break main_loop;
                    }
                    if ( off == maxOff )
                    {
                        rleState = 3;
                        rleCharacter = ch;
                        break main_loop;
                    }
                    ch = buf[off++] & 0xFF;
                    crc = ( crc << 8 ) ^ crc_table[( crc >>> 24 ) ^ ch];
                    if ( ch != last )
                        continue;

                    /* === STATE 4+ === */
                    assert size < maxSize - 1;
                    block[size++] = (byte) ( ch + CHARACTER_BIAS );

                    /*
                     * While the run is shorter than MAX_RUN_LENGTH characters, keep trying to append more characters to
                     * it.
                     */
                    for ( int run = 4; run < MAX_RUN_LENGTH; run++ )
                    {
                        /* Check for end of input buffer. */
                        if ( off == maxOff )
                        {
                            rleState = run;
                            rleCharacter = ch;
                            break main_loop;
                        }

                        /* Fetch the next character. */
                        ch = buf[off++] & 0xFF;
                        int savedCrc = crc;
                        crc = ( crc << 8 ) ^ crc_table[( crc >>> 24 ) ^ ch];

                        /*
                         * If the character does not match, terminate the current run and start a fresh one.
                         */
                        if ( ch != last )
                        {
                            block[size++] = (byte) ( run - 4 + CHARACTER_BIAS );
                            inuse[run - 4] = true;
                            if ( size < maxSize )
                                continue state1;

                            /*
                             * There is no space left to begin a new run. Unget the last character and finish.
                             */
                            off--;
                            crc = savedCrc;
                            rleState = -1;
                            break main_loop;
                        }
                    }

                    /*
                     * The run has reached maximal length, so it must be ended prematurely.
                     */
                    block[size++] = (byte) ( MAX_RUN_LENGTH - 4 + CHARACTER_BIAS );
                    inuse[MAX_RUN_LENGTH - 4] = true;
                    break;
                }
            }
        }

        this.size = size;
        this.crc = crc;
        return len - ( maxOff - off );
    }

    public CompressedBlock compress()
    {
        if ( size < MIN_BLOCK_SIZE )
            throw new IllegalStateException( "Cannot compress empty block" );

        /* Finalize initial RLE. */
        if ( rleState >= 4 )
        {
            assert ( size < maxSize );
            block[size++] = (byte) ( rleState - 4 + CHARACTER_BIAS );
            inuse[rleState - 4] = true;
        }

        CompressedBlock compressedBlock = new CompressedBlock( this );

        /* Reset block to the initial state. */
        Arrays.fill( inuse, false );
        rleState = 0;
        crc = -1;
        size = 0;

        return compressedBlock;
    }
}
