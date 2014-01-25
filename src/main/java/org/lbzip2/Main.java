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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.lbzip2.impl.LBzip2InputStream;
import org.lbzip2.impl.MBC;

/**
 * @author Mikolaj Izdebski
 */
public class Main
{
    private static void processFile( InputStream is )
        throws IOException
    {
        if ( System.getProperty( "org.lbzip2.mbc" ) != null )
        {
            MBC mbc = new MBC( is, System.out );
            mbc.expand();
        }
        else
        {
            InputStream zis = new LBzip2InputStream( is );

            byte[] buf = new byte[4096];
            int r;
            while ( ( r = zis.read( buf ) ) != -1 )
                System.out.write( buf, 0, r );

            zis.close();
        }
    }

    public static void main( String[] args )
    {
        try
        {
            if ( args.length != 0 )
            {
                for ( String arg : args )
                {
                    processFile( new FileInputStream( arg ) );
                }
            }
            else
            {
                processFile( System.in );
            }
        }
        catch ( IOException e )
        {
            e.printStackTrace();
        }
    }
}
