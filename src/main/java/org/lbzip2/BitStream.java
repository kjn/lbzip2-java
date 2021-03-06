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

import static org.lbzip2.Status.FINISH;
import static org.lbzip2.Status.MORE;
import static org.lbzip2.Status.OK;

/**
 * @author Mikolaj Izdebski
 */
class BitStream
{
    /**
     * The bit buffer (left-justified).
     */
    long buff;

    /**
     * Number of bits remaining in the bit buffer.
     */
    int live;

    byte[] ptr;

    int off;

    int len;

    boolean eof;

    Status need( int n )
    {
        while ( live < n && off < len )
        {
            live += 8;
            buff += ( ptr[off++] & 0xFFL ) << ( 64 - live );
        }
        if ( live >= n )
            return OK;
        if ( eof )
            return FINISH;
        return MORE;
    }

    int peek( int n )
    {
        return (int) ( buff >>> ( 64 - n ) );
    }

    void dump( int n )
    {
        buff <<= n;
        live -= n;
    }

    int take( int n )
    {
        assert ( live >= n );
        int x = peek( n );
        dump( n );
        return x;
    }

    void align()
    {
        dump( live % 8 );
    }

    void consume()
    {
        dump( live );
        off = len;
    }
}
