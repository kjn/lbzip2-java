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

import static org.lbzip2.impl.Constants.MAX_ALPHA_SIZE;
import static org.lbzip2.impl.Constants.MAX_SELECTORS;
import static org.lbzip2.impl.Constants.MAX_TREES;

/**
 * Retriever parses and decodes <em>bz2</em> streams into internal structures.
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

    /**
     * Current state of retriever FSA.
     */
    State state;

    /**
     * Coding tree selectors.
     */
    byte[] selector;

    /**
     * Number of prefix trees used.
     */
    int num_trees;

    /**
     * Number of tree selectors present.
     */
    int num_selectors;

    /**
     * Number of distinct prefix codes.
     */
    int alpha_size;

    byte[] code_len;

    /**
     * Current state of inverse MTF FSA.
     */
    int[] mtf;

    /**
     * Coding trees.
     */
    PrefixDecoder[] tree;

    /**
     * Big descriptor of the bitmap.
     */
    short big;

    /**
     * small descriptor of the bitmap.
     */
    short small;

    /**
     * General purpose index.
     */
    int j;

    /**
     * Current tree number.
     */
    int t;

    /**
     * Current group number.
     */
    int g;

    MtfDecoder mtf_d;

    int runChar;

    int run;

    int shift;

    public Retriever()
    {
        this.selector = new byte[MAX_SELECTORS];
        this.code_len = new byte[MAX_ALPHA_SIZE];
        this.mtf = new int[MAX_TREES];
        this.tree = new PrefixDecoder[MAX_TREES];
        this.mtf_d = new MtfDecoder();
    }
}
