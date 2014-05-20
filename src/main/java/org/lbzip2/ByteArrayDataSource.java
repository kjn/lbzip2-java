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
class ByteArrayDataSource
    extends AbstractDataSource
{
    private final byte[] array;

    private int offset;

    public ByteArrayDataSource( byte[] array )
    {
        this.array = array;
    }

    public boolean isEmpty()
        throws IOException
    {
        return offset == array.length;
    }

    public int read( byte[] buf, int off, int len )
        throws IOException
    {
        int size = Math.min( len, array.length - offset );
        System.arraycopy( array, offset, buf, off, size );
        return size;
    }
}