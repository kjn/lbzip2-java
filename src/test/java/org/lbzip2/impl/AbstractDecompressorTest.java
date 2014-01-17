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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

import org.junit.Test;

/**
 * @author Mikolaj Izdebski
 */
public abstract class AbstractDecompressorTest
{
    public String md5( byte[] arr )
        throws Exception
    {
        MessageDigest md = MessageDigest.getInstance( "MD5" );
        md.update( arr );
        StringBuilder sb = new StringBuilder();
        for ( byte c : md.digest() )
            sb.append( String.format( "%02x", c ) );
        return sb.toString();
    }

    protected abstract void oneFile( InputStream fis, String md5 )
        throws Exception;

    private void oneFile( String name, String md5 )
        throws Exception
    {
        InputStream fis = new FileInputStream( "test-data/" + name + ".bz2" );

        try
        {
            oneFile( fis, md5 );
        }
        finally
        {
            try
            {
                fis.close();
            }
            catch ( IOException e )
            {
                throw e;
            }
        }
    }

    @Test
    public void testExpand()
        throws Exception
    {
        oneFile( "32767", "2debfdcf79f03e4a65a667d21ef9de14" );
        oneFile( "ch255", "3d58e437e3685a77276da1199aace837" );
        oneFile( "codelen20", "7fc56270e7a70fa81a5935b72eacbe29" );
        oneFile( "concat", "2debfdcf79f03e4a65a667d21ef9de14" );
        oneFile( "crc1", null );
        oneFile( "crc2", null );
        oneFile( "cve2", null );
        oneFile( "cve", null );
        oneFile( "empty", "d41d8cd98f00b204e9800998ecf8427e" );
        oneFile( "fib", "4d702db5a6f546feb9f739aa946b4f56" );
        oneFile( "gap", "2debfdcf79f03e4a65a667d21ef9de14" );
        oneFile( "incomp-1", "8bab041a19e4cf0c244da418b15676f1" );
        oneFile( "incomp-2", "8bab041a19e4cf0c244da418b15676f1" );
        oneFile( "load0", null );
        oneFile( "overrun", null );
        oneFile( "rand", "2debfdcf79f03e4a65a667d21ef9de14" );
        oneFile( "repet", "bf3335ea7e712d847d189b087e45c54b" );
        oneFile( "trash", "2debfdcf79f03e4a65a667d21ef9de14" );
        oneFile( "void", null );
    }
}
