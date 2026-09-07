package com.jftse.emulator.server.core.service.impl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Documents the independently dumped retail Item_PetItem.set table.
 * MaxUse values are resource metadata only. Their server meaning
 * (per-item use count vs value cap vs UI) is unproven and must not
 * be enforced as a durable counter.
 */
class PetItemRetailResourceTableTest {
    private static final List<Integer> RETAIL_INDICES = List.of(
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14,
            16, 17, 18, 19, 20, 21, 22, 23);

    private static final Map<Integer, Integer> MAX_USE = Map.ofEntries(
            Map.entry(1, 10), Map.entry(2, 10), Map.entry(3, 10), Map.entry(4, 10),
            Map.entry(5, 10), Map.entry(6, 10), Map.entry(7, 10), Map.entry(8, 10),
            Map.entry(9, 10), Map.entry(10, 10), Map.entry(11, 10), Map.entry(12, 10),
            Map.entry(13, 300), Map.entry(14, 50),
            Map.entry(16, 50), Map.entry(17, 50), Map.entry(18, 50), Map.entry(19, 50),
            Map.entry(20, 50), Map.entry(21, 50), Map.entry(22, 50), Map.entry(23, 50));

    @Test
    void retailTableHasExactlyIndices1To14And16To23() {
        assertEquals(22, RETAIL_INDICES.size());
        assertEquals(IntStream.concat(IntStream.rangeClosed(1, 14), IntStream.rangeClosed(16, 23))
                .boxed().toList(), RETAIL_INDICES);
        assertFalse(RETAIL_INDICES.contains(15));
        assertEquals(MAX_USE.keySet().stream().sorted().toList(), RETAIL_INDICES);
        assertEquals(10, MAX_USE.get(1));
        assertEquals(300, MAX_USE.get(13));
        assertEquals(50, MAX_USE.get(14));
        assertEquals(50, MAX_USE.get(23));
    }
}
