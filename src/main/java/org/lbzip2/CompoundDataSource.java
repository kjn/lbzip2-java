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
import java.util.LinkedList;

/**
 * @author Mikolaj Izdebski
 */
class CompoundDataSource
    extends AbstractDataSource
{
    private final LinkedList<DataSource> sources = new LinkedList<DataSource>();

    protected final void addSource( DataSource source )
    {
        sources.add( source );
    }

    public final boolean isEmpty()
        throws IOException
    {
        return sources.isEmpty();
    }

    public final int read( byte[] buf, int off, int len )
        throws IOException
    {
        int avail = len;

        while ( avail > 0 && !isEmpty() )
        {
            DataSource source = sources.peek();

            int read = source.read( buf, off, avail );
            off += read;
            avail -= read;

            if ( source.isEmpty() )
                sources.poll();
        }

        return len - avail;
    }
}