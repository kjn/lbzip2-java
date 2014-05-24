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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Mikolaj Izdebski
 */
public class Main
{
    private static void copyStream( InputStream is, OutputStream os )
        throws IOException
    {
        byte[] buf = new byte[4096];
        int r;
        while ( ( r = is.read( buf ) ) != -1 )
            os.write( buf, 0, r );

    }

    private static void compress( InputStream is, OutputStream os )
        throws IOException
    {
        LBzip2OutputStream zos = new LBzip2OutputStream( os, 900000 );
        copyStream( is, zos );
        zos.finish();
    }

    private static void decompress( InputStream is, OutputStream os )
        throws IOException
    {
        if ( System.getProperty( "org.lbzip2.mbc" ) != null )
        {
            MBC mbc = new MBC( is, os );
            mbc.expand();
        }
        else
        {
            InputStream zis = new LBzip2InputStream( is );
            copyStream( zis, os );
        }
    }

    private static void processStream( InputStream is, OutputStream os )
        throws IOException
    {
        if ( decompress )
        {
            decompress( is, os );
        }
        else
        {
            compress( is, os );
        }
    }

    private static boolean decompress;

    public static void main( String[] args )
    {
        try
        {
            decompress = args.length > 0 && args[0].equals( "-d" );

            processStream( System.in, System.out );
        }
        catch ( IOException e )
        {
            e.printStackTrace();
        }
    }
}
