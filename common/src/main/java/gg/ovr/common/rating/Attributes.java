package gg.ovr.common.rating;

/**
 * The six card attributes (SCORING.md §4), each on a 0–100 scale.
 * Same shape for one match and for the aggregated card.
 */
public record Attributes(double agr, double frm, double vis, double tea, double sur, double imp) {

    public Attributes {
        requireScore("agr", agr);
        requireScore("frm", frm);
        requireScore("vis", vis);
        requireScore("tea", tea);
        requireScore("sur", sur);
        requireScore("imp", imp);
    }

    private static void requireScore(String name, double value) {
        // Written so that NaN fails too.
        if (!(value >= 0 && value <= 100)) {
            throw new IllegalArgumentException(name + " must be between 0 and 100, was " + value);
        }
    }
}
