/*
 * DynamicJava - Copyright (C) 1999 Dyade
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions: The above copyright notice and this
 * permission notice shall be included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL DYADE BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH
 * THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * 
 * Except as contained in this notice, the name of Dyade shall not be used in advertising or
 * otherwise to promote the sale, use or other dealings in this Software without prior written
 * authorization from Dyade.
 */

package koala.dynamicjava.tree;

/**
 * This class represents the allocation nodes of the syntax tree
 * 
 * @author Stephane Hillion
 * @version 1.0 - 1999/04/25
 */

public abstract class Allocation extends PrimaryExpression {
    /**
     * The creationType property name
     */
    public final static String CREATION_TYPE = "creationType";

    /**
     * The creationType
     */
    private Type creationType;

    /**
     * Initializes the expression
     * 
     * @param tp the creation type
     * @param fn the filename
     * @param bl the begin line
     * @param bc the begin column
     * @param el the end line
     * @param ec the end column
     * @exception IllegalArgumentException if tp is null
     */
    protected Allocation(final Type tp, final String fn, final int bl, final int bc, final int el, final int ec) {
        super(fn, bl, bc, el, ec);

        if (tp == null) {
            throw new IllegalArgumentException("tp == null");
        }

        this.creationType = tp;
    }

    /**
     * Returns the creation type
     */
    public Type getCreationType() {
        return this.creationType;
    }

    /**
     * Sets the creation type
     * 
     * @exception IllegalArgumentException if t is null
     */
    public void setCreationType(final Type t) {
        if (t == null) {
            throw new IllegalArgumentException("t == null");
        }

        firePropertyChange(CREATION_TYPE, this.creationType, this.creationType = t);
    }
}
