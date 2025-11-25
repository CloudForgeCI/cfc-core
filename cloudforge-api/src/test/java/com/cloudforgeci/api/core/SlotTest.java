package com.cloudforgeci.api.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for Slot class.
 *
 * Tests the slot mechanism for deferred value injection:
 * - Setting and getting values
 * - Optional value retrieval
 * - Callback registration (waiters)
 * - Idempotency (set only once)
 */
class SlotTest {

    @Test
    void testSlotSetAndGet() {
        // Given: An empty slot
        Slot<String> slot = new Slot<>();

        // When: Setting a value
        slot.set("test-value");

        // Then: Should retrieve the value
        Optional<String> result = slot.get();
        assertTrue(result.isPresent());
        assertEquals("test-value", result.get());
    }

    @Test
    void testSlotGetEmptyReturnsEmpty() {
        // Given: An empty slot
        Slot<String> slot = new Slot<>();

        // When: Getting without setting
        Optional<String> result = slot.get();

        // Then: Should return empty Optional
        assertFalse(result.isPresent());
        assertEquals(Optional.empty(), result);
    }

    @Test
    void testSlotSetOnlyOnce() {
        // Given: A slot with a value
        Slot<String> slot = new Slot<>();
        slot.set("first-value");

        // When: Attempting to set again
        slot.set("second-value");

        // Then: Should keep the first value (idempotent)
        assertEquals("first-value", slot.get().get());
    }

    @Test
    void testSlotOnSetCallbackCalledImmediatelyIfValueExists() {
        // Given: A slot with a value already set
        Slot<String> slot = new Slot<>();
        slot.set("existing-value");

        // When: Registering a callback
        List<String> receivedValues = new ArrayList<>();
        slot.onSet(receivedValues::add);

        // Then: Callback should be called immediately
        assertEquals(1, receivedValues.size());
        assertEquals("existing-value", receivedValues.get(0));
    }

    @Test
    void testSlotOnSetCallbackCalledWhenValueSet() {
        // Given: An empty slot with a registered callback
        Slot<String> slot = new Slot<>();
        List<String> receivedValues = new ArrayList<>();
        slot.onSet(receivedValues::add);

        // When: Setting a value
        slot.set("new-value");

        // Then: Callback should be invoked
        assertEquals(1, receivedValues.size());
        assertEquals("new-value", receivedValues.get(0));
    }

    @Test
    void testSlotMultipleCallbacks() {
        // Given: An empty slot with multiple callbacks
        Slot<Integer> slot = new Slot<>();
        AtomicInteger counter1 = new AtomicInteger(0);
        AtomicInteger counter2 = new AtomicInteger(0);
        AtomicInteger counter3 = new AtomicInteger(0);

        slot.onSet(v -> counter1.set(v));
        slot.onSet(v -> counter2.set(v));
        slot.onSet(v -> counter3.set(v));

        // When: Setting a value
        slot.set(42);

        // Then: All callbacks should be invoked
        assertEquals(42, counter1.get());
        assertEquals(42, counter2.get());
        assertEquals(42, counter3.get());
    }

    @Test
    void testSlotCallbacksAreClearedAfterSet() {
        // Given: A slot with callbacks
        Slot<String> slot = new Slot<>();
        AtomicInteger callCount = new AtomicInteger(0);
        slot.onSet(v -> callCount.incrementAndGet());

        // When: Setting value twice
        slot.set("first");
        slot.set("second");  // Should not trigger callback again

        // Then: Callback should only be called once
        assertEquals(1, callCount.get());
    }

    @Test
    void testSlotWithNullValue() {
        // Given: A slot
        Slot<String> slot = new Slot<>();

        // When: Setting null
        slot.set(null);

        // Then: get() should return empty (null is not stored)
        assertFalse(slot.get().isPresent());
    }

    @Test
    void testSlotWithComplexObject() {
        // Given: A slot for complex objects
        Slot<List<String>> slot = new Slot<>();
        List<String> testList = List.of("a", "b", "c");

        // When: Setting a list
        slot.set(testList);

        // Then: Should retrieve the same list
        assertEquals(testList, slot.get().get());
    }

    @Test
    void testSlotOnSetWithMultipleValueTypes() {
        // Given: Slots for different types
        Slot<Integer> intSlot = new Slot<>();
        Slot<String> stringSlot = new Slot<>();
        Slot<Boolean> boolSlot = new Slot<>();

        // When: Setting values and registering callbacks
        AtomicBoolean intCalled = new AtomicBoolean(false);
        AtomicBoolean stringCalled = new AtomicBoolean(false);
        AtomicBoolean boolCalled = new AtomicBoolean(false);

        intSlot.onSet(v -> intCalled.set(true));
        stringSlot.onSet(v -> stringCalled.set(true));
        boolSlot.onSet(v -> boolCalled.set(true));

        intSlot.set(123);
        stringSlot.set("test");
        boolSlot.set(true);

        // Then: All callbacks should be called
        assertTrue(intCalled.get());
        assertTrue(stringCalled.get());
        assertTrue(boolCalled.get());
    }

    @Test
    void testSlotCallbackOrderPreserved() {
        // Given: A slot with ordered callbacks
        Slot<Integer> slot = new Slot<>();
        List<Integer> callOrder = new ArrayList<>();

        slot.onSet(v -> callOrder.add(1));
        slot.onSet(v -> callOrder.add(2));
        slot.onSet(v -> callOrder.add(3));

        // When: Setting value
        slot.set(100);

        // Then: Callbacks should be called in registration order
        assertEquals(List.of(1, 2, 3), callOrder);
    }

    @Test
    void testSlotImmutabilityAfterSet() {
        // Given: A slot with a value
        Slot<String> slot = new Slot<>();
        slot.set("immutable");

        // When: Trying to set multiple times
        slot.set("change1");
        slot.set("change2");
        slot.set("change3");

        // Then: Value should remain unchanged
        assertEquals("immutable", slot.get().get());
    }

    @Test
    void testSlotEmptyGetOrElse() {
        // Given: An empty slot
        Slot<String> slot = new Slot<>();

        // When: Using orElse
        String result = slot.get().orElse("default-value");

        // Then: Should return default
        assertEquals("default-value", result);
    }

    @Test
    void testSlotPopulatedGetOrElse() {
        // Given: A slot with a value
        Slot<String> slot = new Slot<>();
        slot.set("actual-value");

        // When: Using orElse
        String result = slot.get().orElse("default-value");

        // Then: Should return actual value
        assertEquals("actual-value", result);
    }
}
