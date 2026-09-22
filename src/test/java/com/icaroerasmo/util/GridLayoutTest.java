package com.icaroerasmo.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GridLayoutTest {

    private static final double SIXTEEN_NINE = 16.0 / 9.0;

    @ParameterizedTest
    @CsvSource({
            "1, 1, 1",
            "2, 2, 1",
            "3, 3, 1",
            "4, 2, 2",
            "5, 3, 2",
            "6, 3, 2",
            "7, 4, 2",
            "8, 4, 2",
            "9, 3, 3",
            "10, 5, 2",
            "12, 4, 3"
    })
    void deveComputarGradesEsperadas(int count, int expectedColumns, int expectedRows) {
        GridLayout.Grid grid = GridLayout.compute(count, SIXTEEN_NINE);
        assertEquals(new GridLayout.Grid(expectedColumns, expectedRows), grid,
                () -> "layout inesperado para " + count + " cameras");
    }

    @Test
    void deveRejeitarContagemInvalida() {
        assertThrows(IllegalArgumentException.class, () -> GridLayout.compute(0, SIXTEEN_NINE));
        assertThrows(IllegalArgumentException.class, () -> GridLayout.compute(-3, SIXTEEN_NINE));
    }

    @Test
    void gradeSempreCobreTodasAsCelulas() {
        for (int count = 1; count <= 64; count++) {
            GridLayout.Grid grid = GridLayout.compute(count, SIXTEEN_NINE);
            assertEquals(true, grid.cells() >= count,
                    "grade " + grid + " nao cobre " + count + " celulas");
        }
    }
}