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

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import org.junit.Test;

/**
 * @author Mikolaj Izdebski
 */
public class LBzip2OutputStreamTest
{
    /**
     * Test compression of empty file.
     * 
     * @throws Exception
     */
    @Test
    public void testEmptyStream()
        throws Exception
    {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        new LBzip2OutputStream( os, 42 ).close();
        byte[] out = os.toByteArray();

        byte[] exp =
            new byte[] { 0x42, 0x5a, 0x68, 0x31, 0x17, 0x72, 0x45, 0x38, 0x50, (byte) 0x90, 0x00, 0x00, 0x00, 0x00 };

        assertEquals( exp.length, out.length );

        for ( int i = 0; i < exp.length; i++ )
        {
            assertEquals( "i=" + i, exp[i], out[i] );
        }
    }

    /**
     * Regression test for bug nr 1.
     * <p>
     * See: https://github.com/kjn/lbzip2-java/issues/1
     * 
     * @throws Exception
     */
    @Test
    public void testBug1()
        throws Exception
    {
        OutputStream os = new LBzip2OutputStream( new ByteArrayOutputStream(), 900000 );
        os.write( 'c' );
        os.close();
    }
}
