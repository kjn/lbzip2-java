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
 * Retriever parses and decodes <em>bz2</em> streams into internal <em>lbzip2</em> structures.
 * 
 * @author Mikolaj Izdebski
 */
class Retriever
{
    /**
     * FSM states from which retriever can be started or resumed.
     */
    enum State
    {
        /**
         * State indicating that retriever was just initialized.
         */
        S_INIT,

        /**
         * State indicating that retriever was stalled while reading BWT primary index.
         */
        S_BWT_IDX,

        /**
         * State indicating that retriever was stalled while reading big bucket selector of character map.
         */
        S_BITMAP_BIG,

        /**
         * State indicating that retriever was stalled while reading small bucket selector of character map.
         */
        S_BITMAP_SMALL,

        /**
         * State indicating that retriever was stalled while reading tree selector MTF value.
         */
        S_SELECTOR_MTF,

        /**
         * State indicating that retriever was stalled while reading tree delta code.
         */
        S_DELTA_TAG,

        /**
         * State indicating that retriever was stalled while reading symbol prefix code.
         */
        S_PREFIX,
    };
}
