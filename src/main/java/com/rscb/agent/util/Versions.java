package com.rscb.agent.util;

/**
 * Perbandingan versi numeric-dot, port dari logika backend
 * (AgentService.compareVersions): segmen dipisah ".", dibandingkan
 * numerik, segmen yang hilang dianggap 0. Segmen non-numerik melempar
 * {@link NumberFormatException}; pemanggil memutuskan fallback-nya
 * (kesetaraan string), sama seperti backend.
 */
public final class Versions {

    private Versions() {
    }

    public static int compare(String a, String b) {
        int[] pa = parse(a);
        int[] pb = parse(b);
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int na = i < pa.length ? pa[i] : 0;
            int nb = i < pb.length ? pb[i] : 0;
            if (na > nb) {
                return 1;
            }
            if (na < nb) {
                return -1;
            }
        }
        return 0;
    }

    public static boolean isNewer(String candidate, String current) {
        try {
            return compare(candidate, current) > 0;
        } catch (NumberFormatException e) {
            return !candidate.equals(current);
        }
    }

    private static int[] parse(String version) {
        String[] parts = version.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Integer.parseInt(parts[i].trim());
        }
        return out;
    }
}
