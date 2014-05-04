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
package org.lbzip2;

import static org.lbzip2.Constants.crc_table;
import junit.framework.TestCase;

/**
 * Unit tests for CRC-32 generation.
 * 
 * @author Mikolaj Izdebski
 */
public class CrcTest
    extends TestCase
{
    private int crcOf( String text )
        throws Exception
    {
        int crc = -1;
        for ( byte b : text.getBytes( "US-ASCII" ) )
            crc = ( crc << 8 ) ^ crc_table[( crc >>> 24 ) ^ ( b & 0xff )];
        return crc ^ -1;
    }

    /**
     * Test if CRC-32 table is computed correctly.
     * <p>
     * Expected CRC values can be generated using command like:<br>
     * {@code echo -n TEST | bzip2 | bzip2 -tvvv}.
     * 
     * @throws Exception
     */
    public void testCrc()
        throws Exception
    {
        assertEquals( 0x00000000, crcOf( "" ) );
        assertEquals( 0x5af7997b, crcOf( "TEST" ) );
        assertEquals( 0xd5573984, crcOf( "AnOtHeR=TeSt" ) );
    }
}
