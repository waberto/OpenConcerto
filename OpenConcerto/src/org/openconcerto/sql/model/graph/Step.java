/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2011 OpenConcerto, by ILM Informatique. All rights reserved.
 * 
 * The contents of this file are subject to the terms of the GNU General Public License Version 3
 * only ("GPL"). You may not use this file except in compliance with the License. You can obtain a
 * copy of the License at http://www.gnu.org/licenses/gpl-3.0.html See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each file.
 */
 
 package org.openconcerto.sql.model.graph;

import org.openconcerto.sql.model.SQLField;
import org.openconcerto.sql.model.SQLTable;
import org.openconcerto.sql.model.graph.Link.Direction;
import org.openconcerto.utils.CollectionUtils;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.jcip.annotations.ThreadSafe;

/**
 * An immutable step in a {@link Path}.
 * 
 * @author Sylvain CUAZ
 */
@ThreadSafe
public class Step {

    /**
     * Create a new step crossing <code>fField</code>.
     * 
     * @param start the start of the step.
     * @param fField a foreign field.
     * @param direction how to cross <code>fField</code>, <code>ANY</code> to infer it.
     * @return a new step.
     * @throws IllegalArgumentException if <code>fField</code> is not a foreign field, if neither of
     *         its ends are <code>start</code>, if <code>direction</code> is ANY and
     *         <code>fField</code> points to its source, or if <code>direction</code> is not
     *         <code>ANY</code> and wrong.
     */
    public static final Step create(final SQLTable start, final SQLField fField, final Direction direction) throws IllegalArgumentException {
        final Link l = fField.getDBSystemRoot().getGraph().getForeignLink(fField);
        if (l == null)
            throw new IllegalArgumentException(fField + " is not a foreign field.");
        // throws an exception if l is not connected to start
        final SQLTable end = l.oppositeVertex(start);
        final SQLTable fieldStart = fField.getTable();

        final Direction computedDirection;
        if (start == end)
            computedDirection = Direction.ANY;
        else
            computedDirection = Direction.fromForeign(fieldStart == start);

        if (computedDirection == Direction.ANY && direction == Direction.ANY)
            throw new IllegalArgumentException("the field references its table: " + fField + ", you must specify the direction");
        if (direction != Direction.ANY && computedDirection != Direction.ANY && direction != computedDirection)
            throw new IllegalArgumentException("wrong direction: " + direction + ", real is : " + computedDirection);
        final Direction nonNullDir = direction == Direction.ANY ? computedDirection : direction;
        assert nonNullDir != Direction.ANY;

        return new Step(start, fField, nonNullDir, end);
    }

    public static final Step create(final SQLTable start, final SQLTable end) {
        final Set<SQLField> set = start.getDBSystemRoot().getGraph().getFields(start, end);
        if (set.isEmpty())
            throw new IllegalArgumentException("path is broken between " + start + " and " + end);
        return create(start, set, end);
    }

    // no-check : fields must be between start and end
    private static final Step create(final SQLTable start, final Set<SQLField> jFields, final SQLTable end) {
        if (start == end)
            throw new IllegalArgumentException("start and end are the same: " + start + " the direction can't be inferred");
        final Map<SQLField, Direction> fields = new HashMap<SQLField, Direction>(jFields.size());
        for (final SQLField f : jFields)
            fields.put(f, Direction.fromForeign(start == f.getTable()));
        return new Step(start, fields, CollectionUtils.getSole(fields.keySet()), end);
    }

    public static final Step create(final SQLTable start, final Collection<Link> links) {
        if (links.size() == 0)
            throw new IllegalArgumentException("empty fields");
        final SQLTable end = links.iterator().next().oppositeVertex(start);
        final Set<SQLField> set = new HashSet<SQLField>();
        for (final Link l : links) {
            if (end != l.oppositeVertex(start))
                throw new IllegalArgumentException("fields do not point to the same table: " + links);
            set.add(l.getLabel());
        }

        return create(start, set, end);
    }

    private final SQLTable from;
    private final SQLTable to;
    private final Map<SQLField, Direction> fields;
    // after profiling: doing getStep().iterator().next() costs a lot
    private final SQLField singleField;

    // all constructors are private since they don't fully check the coherence of their parameters
    private Step(final SQLTable start, final Map<SQLField, Direction> fields, final SQLField singleField, final SQLTable end) {
        assert start != null && end != null;
        assert fields.size() > 0;
        assert CollectionUtils.getSole(fields.keySet()) == singleField;
        assert !new HashSet<Direction>(fields.values()).contains(Direction.ANY) : "some directions are unknown : " + fields;
        // thread-safe since only mutable attributes are volatile
        assert fields instanceof AbstractMap : "Fields might not be thread-safe";
        this.from = start;
        this.to = end;
        this.fields = Collections.unmodifiableMap(fields);
        this.singleField = singleField;
    }

    private Step(final SQLTable start, final Map<SQLField, Direction> fields, final SQLTable end) {
        this(start, new HashMap<SQLField, Direction>(fields), CollectionUtils.getSole(fields.keySet()), end);
    }

    private Step(final SQLTable start, SQLField field, final Direction foreign, final SQLTable end) {
        this(start, Collections.singletonMap(field, foreign), field, end);
    }

    public Step(Step p) {
        this(p.from, p.fields, p.to);
    }

    public final Step reverse() {
        final Map<SQLField, Direction> reverseFields = new HashMap<SQLField, Direction>(this.fields.size());
        for (final Entry<SQLField, Direction> e : this.fields.entrySet()) {
            reverseFields.put(e.getKey(), e.getValue().reverse());
        }
        return new Step(this.to, reverseFields, this.from);
    }

    public final SQLTable getFrom() {
        return this.from;
    }

    public final SQLTable getTo() {
        return this.to;
    }

    public final Set<SQLField> getFields() {
        return this.fields.keySet();
    }

    public final SQLField getSingleField() {
        return this.singleField;
    }

    public final Set<Step> getSingleSteps() {
        if (this.singleField != null)
            return Collections.singleton(this);
        final Set<Step> res = new HashSet<Step>(this.fields.size());
        for (final Entry<SQLField, Direction> e : this.fields.entrySet()) {
            res.add(new Step(this.getFrom(), e.getKey(), e.getValue(), this.getTo()));
        }
        return res;
    }

    /**
     * Whether this step goes through the field <code>f</code> forwards or backwards.
     * 
     * @param f the field.
     * @return <code>true</code> if f is crossed forwards (e.g. going from SITE to CONTACT with
     *         ID_CONTACT_CHEF and ID_CONTACT_BUREAU).
     */
    public final boolean isForeign(final SQLField f) {
        return this.getDirection(f) == Direction.FOREIGN;
    }

    /**
     * Whether this step goes through the field <code>f</code> forwards or backwards.
     * 
     * @param f the field.
     * @return <code>FOREIGN</code> if f is crossed forwards (e.g. going from SITE to CONTACT with
     *         ID_CONTACT_CHEF and ID_CONTACT_BUREAU), <code>REFERENT</code> otherwise.
     */
    public final Direction getDirection(final SQLField f) {
        return this.fields.get(f);
    }

    /**
     * Whether this step goes through all of its fields forwards or backwards.
     * 
     * @return <code>true</code> if all fields are forwards, <code>null</code> if mixed.
     * @see #isForeign(SQLField)
     */
    public final Boolean isForeign() {
        final Direction soleDir = getDirection();
        return soleDir == Direction.ANY ? null : soleDir == Direction.FOREIGN;
    }

    /**
     * Whether this step goes through all of its fields forwards or backwards.
     * 
     * @return <code>FOREIGN</code> or <code>REFERENT</code> if all fields go the same way,
     *         <code>ANY</code> if mixed.
     * @see #isForeign(SQLField)
     */
    public final Direction getDirection() {
        final Direction soleDir = CollectionUtils.getSole(new HashSet<Direction>(this.fields.values()));
        return soleDir == null ? Direction.ANY : soleDir;
    }

    public String toString() {
        return this.getClass().getSimpleName() + " from: " + this.getFrom() + " to: " + this.getTo() + "\n" + this.fields;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Step) {
            final Step o = (Step) obj;
            // no need to compare to, starting from the same point with the same fields lead to the
            // same table
            return this.from.equals(o.from) && this.fields.equals(o.fields);
        } else
            return false;
    }

    /**
     * Returns a hash code value for this step.
     * 
     * @return a hash code value for this object.
     */
    @Override
    public int hashCode() {
        return this.fields.hashCode();
    }
}
