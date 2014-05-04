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

/**
 * @author Mikolaj Izdebski
 */
public class StreamDecomposer
    implements DataSink
{
    public boolean isFull()
        throws IOException
    {
        // TODO Auto-generated method stub
        return false;
    }

    public int write( int b )
        throws IOException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    public int write( byte[] buf )
        throws IOException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    public int write( byte[] buf, int off, int len )
        throws IOException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    public UndecompressedBlock extractBlock()
    {
        // TODO Auto-generated method stub
        return null;
    }
}
