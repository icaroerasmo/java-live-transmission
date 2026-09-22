package com.icaroerasmo.util;

public class GridLayout {

    public record Grid(int columns, int rows) {
        public int cells() {
            return columns * rows;
        }
    }

    public static Grid compute(int count, double targetAspect) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be >= 1");
        }

        Grid best = null;
        double bestScore = Double.MAX_VALUE;
        int bestRows = Integer.MAX_VALUE;

        for (int rows = 1; rows <= count; rows++) {
            int columns = (int) Math.ceil((double) count / rows);
            int empty = columns * rows - count;
            double aspect = (double) columns / rows;
            double score = empty + Math.abs(aspect - targetAspect);

            if (score < bestScore - 1e-12
                    || (Math.abs(score - bestScore) <= 1e-12 && rows < bestRows)) {
                best = new Grid(columns, rows);
                bestScore = score;
                bestRows = rows;
            }
        }

        return best;
    }
}