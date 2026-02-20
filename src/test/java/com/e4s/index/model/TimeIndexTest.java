package com.e4s.index.model;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TimeIndexTest {

    @Test
    void testContains() {
        TimeIndex index = new TimeIndex();
        assertFalse(index.contains(100));
        
        index.add(100);
        assertTrue(index.contains(100));
        assertFalse(index.contains(101));
    }

    @Test
    void testFindPrev() {
        TimeIndex index = new TimeIndex();
        index.add(100);
        index.add(200);
        index.add(300);

        Optional<Integer> prev = index.findPrev(250);
        assertTrue(prev.isPresent());
        assertEquals(200, prev.get());

        prev = index.findPrev(100);
        assertFalse(prev.isPresent());

        prev = index.findPrev(101);
        assertTrue(prev.isPresent());
        assertEquals(100, prev.get());
    }

    @Test
    void testFindNext() {
        TimeIndex index = new TimeIndex();
        index.add(100);
        index.add(200);
        index.add(300);

        Optional<Integer> next = index.findNext(150);
        assertTrue(next.isPresent());
        assertEquals(200, next.get());

        next = index.findNext(300);
        assertFalse(next.isPresent());

        next = index.findNext(299);
        assertTrue(next.isPresent());
        assertEquals(300, next.get());
    }

    @Test
    void testAddAll() {
        TimeIndex index = new TimeIndex();
        index.addAll(new int[]{100, 200, 300});

        assertTrue(index.contains(100));
        assertTrue(index.contains(200));
        assertTrue(index.contains(300));
        assertEquals(3, index.cardinality());
    }

    @Test
    void testSerializeDeserialize() throws IOException {
        TimeIndex original = new TimeIndex();
        original.add(100);
        original.add(200);
        original.add(300);

        byte[] data = original.serialize();
        TimeIndex deserialized = TimeIndex.deserialize(data);

        assertTrue(deserialized.contains(100));
        assertTrue(deserialized.contains(200));
        assertTrue(deserialized.contains(300));
        assertEquals(3, deserialized.cardinality());
    }

    @Test
    void testSerializeDeserializeEmpty() throws IOException {
        TimeIndex original = new TimeIndex();
        byte[] data = original.serialize();
        TimeIndex deserialized = TimeIndex.deserialize(data);
        assertEquals(0, deserialized.cardinality());
    }
}
