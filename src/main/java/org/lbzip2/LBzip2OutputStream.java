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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Mikolaj Izdebski
 */
public class LBzip2OutputStream
    extends OutputStream
{
    private final Logger logger = LoggerFactory.getLogger( LBzip2OutputStream.class );

    private final OutputStream os;

    private final StreamComposer composer;

    private final UncompressedBlock block;

    private final byte[] buf1 = new byte[1];

    private final byte[] buf = new byte[4096];

    public LBzip2OutputStream( OutputStream os, int maxBlockSize )
        throws IOException
    {
        this.os = os;
        composer = new StreamComposer( maxBlockSize );
        block = new UncompressedBlock( maxBlockSize );
    }

    @Override
    public void write( int b )
        throws IOException
    {
        buf1[0] = (byte) b;
        write( buf1, 0, 1 );
    }

    @Override
    public void write( byte[] buf, int off, int avail )
        throws IOException
    {
        while ( avail > 0 )
        {
            int written = block.write( buf, off, avail );
            off += written;
            avail -= written;

            if ( block.isFull() )
            {
                logger.trace( "Block full, forcing transmission" );
                transmit();
            }
        }
    }

    private void transmit()
        throws IOException
    {
        if ( !block.isEmpty() )
        {
            logger.trace( "Adding block" );
            composer.addBlock( block.compress() );
        }

        while ( !composer.isEmpty() )
        {
            logger.trace( "Emptying composer" );
            os.write( buf, 0, composer.read( buf ) );
        }
    }

    @Override
    public void flush()
        throws IOException
    {
        transmit();
        os.flush();
    }

    public void finish()
        throws IOException
    {
        logger.trace( "Closing stream" );
        transmit();
        composer.finish();
        transmit();
    }

    @Override
    public void close()
        throws IOException
    {
        finish();
        os.close();
    }

    public static void main( String[] args )
    {
        try
        {
            InputStream is = System.in;
            OutputStream os = new LBzip2OutputStream( System.out, 900000 );
            byte[] buf = new byte[4096];
            int n;
            while ( ( n = is.read( buf ) ) > 0 )
            {
                os.write( buf, 0, n );
            }
            is.close();
            os.close();
        }
        catch ( Throwable e )
        {
            e.printStackTrace();
        }
    }
}
