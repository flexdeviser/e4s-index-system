package com.e4s.index.model;

import org.roaringbitmap.RoaringBitmap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;

/**
 * A time-based index that stores time periods as a compressed bitmap.
 * 
 * <p>This class wraps a {@link RoaringBitmap} to provide efficient storage
 * and fast lookups for time-based data. It supports:</p>
 * 
 * <ul>
 *   <li>O(1) existence checks</li>
 *   <li>O(log n) previous/next value lookups</li>
 *   <li>Compact storage (~2 bytes per entry for dense data)</li>
 *   <li>Efficient serialization for Redis storage</li>
 * </ul>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * TimeIndex index = new TimeIndex();
 * index.add(19723);  // Add day epoch
 * index.add(19724);
 * 
 * boolean exists = index.contains(19723);  // true
 * Optional<Integer> prev = index.findPrev(19724);  // 19723
 * Optional<Integer> next = index.findNext(19723);  // 19724
 * }</pre>
 * 
 * @author E4S Team
 * @version 1.0.0
 * @see RoaringBitmap
 */
public class TimeIndex {

    private final RoaringBitmap bitmap;

    /**
     * Creates a new empty TimeIndex.
     */
    public TimeIndex() {
        this.bitmap = new RoaringBitmap();
    }

    /**
     * Creates a TimeIndex wrapping an existing RoaringBitmap.
     *
     * @param bitmap the bitmap to wrap
     */
    public TimeIndex(RoaringBitmap bitmap) {
        this.bitmap = bitmap;
    }

    /**
     * Checks if the specified value exists in the index.
     *
     * @param value the value to check
     * @return true if the value exists, false otherwise
     */
    public boolean contains(int value) {
        return bitmap.contains(value);
    }

    /**
     * Finds the previous value before the specified value.
     * 
     * <p>If the exact value exists in the bitmap, returns the value
     * before it. Otherwise, returns the largest value less than the
     * specified value.</p>
     *
     * @param value the reference value
     * @return an Optional containing the previous value, or empty if none exists
     */
    public Optional<Integer> findPrev(int value) {
        long prev = bitmap.previousValue(value);
        if (prev >= 0 && prev < value) {
            return Optional.of((int) prev);
        }
        if (prev == value) {
            prev = bitmap.previousValue(value - 1);
            if (prev >= 0) {
                return Optional.of((int) prev);
            }
        }
        return Optional.empty();
    }

    /**
     * Finds the next value after the specified value.
     * 
     * <p>If the exact value exists in the bitmap, returns the value
     * after it. Otherwise, returns the smallest value greater than the
     * specified value.</p>
     *
     * @param value the reference value
     * @return an Optional containing the next value, or empty if none exists
     */
    public Optional<Integer> findNext(int value) {
        long next = bitmap.nextValue(value);
        if (next > value) {
            return Optional.of((int) next);
        }
        if (next == value) {
            next = bitmap.nextValue(value + 1);
            if (next > value) {
                return Optional.of((int) next);
            }
        }
        return Optional.empty();
    }

    /**
     * Adds a value to the index.
     *
     * @param value the value to add
     */
    public void add(int value) {
        bitmap.add(value);
    }

    /**
     * Adds multiple values to the index.
     *
     * @param values the values to add
     */
    public void addAll(int[] values) {
        for (int v : values) {
            bitmap.add(v);
        }
    }

    /**
     * Serializes this index to a byte array for storage.
     *
     * @return the serialized bytes
     * @throws IOException if serialization fails
     */
    public byte[] serialize() throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            bitmap.serialize(dos);
            return baos.toByteArray();
        }
    }

    /**
     * Deserializes a TimeIndex from a byte array.
     *
     * @param data the serialized bytes
     * @return the deserialized TimeIndex
     * @throws IOException if deserialization fails
     */
    public static TimeIndex deserialize(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            return new TimeIndex();
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             DataInputStream dis = new DataInputStream(bais)) {
            RoaringBitmap bitmap = new RoaringBitmap();
            bitmap.deserialize(dis);
            return new TimeIndex(bitmap);
        }
    }

    /**
     * Returns the size of this index in bytes.
     *
     * @return the size in bytes
     */
    public long sizeInBytes() {
        return bitmap.getSizeInBytes();
    }

    /**
     * Returns the number of values in this index.
     *
     * @return the cardinality
     */
    public int cardinality() {
        return bitmap.getCardinality();
    }
}
