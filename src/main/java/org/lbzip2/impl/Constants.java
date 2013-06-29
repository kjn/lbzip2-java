/*-
 * Copyright (c) 2013 Mikolaj Izdebski
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

/**
 * Various constants specific to <em>bz2</em> file format.
 * 
 * @author Mikolaj Izdebski
 */
class Constants
{
    /**
     * Table for fast computing of CRC-32. {@code crc_table[ch]} is just CRC-32 of a string consisting of a single byte
     * {@code ch}.
     */
    static final int[] crc_table = new int[256];

    /**
     * Initialize CRC table.
     */
    static
    {
        final int POLLY = 0x04c11db7;

        for ( int ch = 0; ch < 256; ch++ )
        {
            int crc = ch << 24;
            for ( int k = 0; k < 8; k++ )
                crc = ( crc << 1 ) ^ POLLY & ( crc >> 31 );
            crc_table[ch] = crc;
        }
    }
}
