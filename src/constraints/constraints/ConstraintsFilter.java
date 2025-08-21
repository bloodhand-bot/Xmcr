package constraints.constraints;

import javafx.util.Pair;
import constraints.config.Configuration;
import java.util.*;

/**
 * Main ConstraintsFilter class that supports two modes:
 * 1. Simple filtering mode (default) - focuses purely on constraint filtering
 * 2. Data extraction mode - includes filtering + data extraction for research
 */
public class ConstraintsFilter {

    // Mode selection
    public static final String MODE_SIMPLE = "simple";
    public static final String MODE_DATA_EXTRACTION = "data_extraction";

    private final ConstraintsFilterSimple simpleFilter;
    private final ConstraintsFilterWithDataExtraction dataExtractionFilter;
    private final String mode;

    /**
     * Constructor that determines mode based on configuration.
     */
    public ConstraintsFilter() {
        this.simpleFilter = new ConstraintsFilterSimple();
        this.dataExtractionFilter = new ConstraintsFilterWithDataExtraction();

        // Determine mode based on system property or default to simple
        this.mode = System.getProperty("constraints.filter.mode", MODE_SIMPLE);
    }

    /**
     * Constructor with explicit mode specification.
     */
    public ConstraintsFilter(String mode) {
        this.simpleFilter = new ConstraintsFilterSimple();
        this.dataExtractionFilter = new ConstraintsFilterWithDataExtraction();
        this.mode = mode != null ? mode : MODE_SIMPLE;
    }

    /**
     * Main filter method that delegates to appropriate implementation based on mode.
     *
     * @param CONS_ASSERT_PO    Program order constraints
     * @param CONS_ASSERT_VALID Lock-related constraints
     * @param causalConstraint  New read-write constraints
     * @return Pair<Boolean, StringBuilder> where Boolean indicates if constraints are satisfiable,
     *         and StringBuilder contains the filtered constraint if available
     */
    public Pair<Boolean, StringBuilder> doFilter_with_expression(StringBuilder CONS_ASSERT_PO, StringBuilder CONS_ASSERT_VALID, StringBuilder causalConstraint) {
        if (MODE_DATA_EXTRACTION.equals(mode)) {
            return dataExtractionFilter.doFilter_with_expression(CONS_ASSERT_PO, CONS_ASSERT_VALID, causalConstraint);
        } else {
            return simpleFilter.doFilter_with_expression(CONS_ASSERT_PO, CONS_ASSERT_VALID, causalConstraint);
        }
    }

    /**
     * Get current mode.
     */
    public String getMode() {
        return mode;
    }

    /**
     * Get statistics for data extraction mode.
     */
    public String getStatistics() {
        if (MODE_DATA_EXTRACTION.equals(mode)) {
            return String.format("Total constraints: %d, Filtered constraints: %d, Filter count: %d",
                    ConstraintsFilterWithDataExtraction.totalConstraintsCounts,
                    ConstraintsFilterWithDataExtraction.filtedConstraintsCounts,
                    ConstraintsFilterWithDataExtraction.filterCount);
        } else {
            return "Statistics not available in simple mode";
        }
    }
}
