package com.main.service;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.*;
import java.util.stream.Collectors;

public class MetasrcService {

    public static class Build {
        public final List<String> runeUrls;
        public final List<String> itemUrls;
        public Build(List<String> runeUrls, List<String> itemUrls) {
            this.runeUrls = runeUrls;
            this.itemUrls = itemUrls;
        }
    }

    // Ítems de inicio / utilidades que NO queremos en “core”
    private static final Set<Integer> STARTER_OR_UTILITY = Set.of(
            1001, 1054,1055,1056,
            2003,2010,2031,2033,
            2055, 2420,
            2138,2139,2140,
            3340,3363,3364
    );

    public Build fetchBuild(String champion, String mode) throws Exception {
        String url = "https://www.metasrc.com/lol/build/" + champion.toLowerCase() + "/" + mode.toLowerCase();
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .referrer("https://www.google.com")
                .timeout(15000)
                .get();

        // SOLO RUNAS (paso 1): 5 primeras con TU selector
        List<String> runes = extractFirstFivePrimaryRunes(doc);

        // Objetos: dejamos tu versión que ya funcionaba bien
        List<String> items = extractCoreItems(doc);

        return new Build(runes, items);
    }

    /**
     * Busca el contenedor de runas (id que termina en "-content") y dentro aplica
     * exactamente el selector: "div:nth-child(1) > div > svg > image".
     * Devuelve como mucho 5 URLs en el orden encontrado.
     */
    private List<String> extractFirstFivePrimaryRunes(Document doc) {
        Element runesRoot = doc.selectFirst("div[id$=-content]");
        if (runesRoot == null) {
            // Fallback por si cambia la estructura: usar todo el doc
            runesRoot = doc;
        }

        Elements images = runesRoot.select("div:nth-child(1) > div > svg > image");
        List<String> urls = new ArrayList<>();
        for (Element e : images) {
            String u = firstNonEmpty(
                    e.attr("xlink:href"),
                    e.attr("href"),
                    e.attr("data-src"),
                    e.attr("src")
            );
            if (u == null || u.isBlank()) continue;
            urls.add(toAbs(u.trim()));
            if (urls.size() == 5) break; // <- solo 5 primeras
        }
        // Evitar duplicados manteniendo orden
        return urls.stream().distinct().collect(Collectors.toList());
    }

    /* ===================== ÍTEMS (fila principal) ===================== */

    private List<String> extractCoreItems(Document doc) {
        // Buscamos contenedores con muchos item-cards "tooltipped" y puntuamos filas
        Elements candidateRows = doc.select(
                "div:has(> div._hmag7l.tooltipped[data-tooltip^=x-item-]), " +
                "div._hmag7l.tooltipped[data-tooltip^=x-item-]"
        );

        Element bestRow = null;
        int bestScore = -1, bestCount = -1;

        for (Element row : candidateRows) {
            Elements tips = row.select("div._hmag7l.tooltipped[data-tooltip^=x-item-]");
            if (tips.isEmpty()) continue;

            List<Integer> ids = new ArrayList<>();
            for (Element tip : tips) {
                int id = parseItemIdFromTooltip(tip.attr("data-tooltip"));
                if (id == -1) {
                    Element img = tip.selectFirst("img");
                    if (img != null) {
                        String src = img.hasAttr("data-src") ? img.attr("data-src") : img.attr("src");
                        id = parseItemIdFromSrc(src);
                    }
                }
                if (id != -1) ids.add(id);
            }
            if (ids.isEmpty()) continue;

            int nonStarter = (int) ids.stream().filter(id -> !STARTER_OR_UTILITY.contains(id)).count();
            int total = ids.size();
            int score = nonStarter * 10 + total;

            if (score > bestScore || (score == bestScore && total > bestCount)) {
                bestScore = score; bestCount = total; bestRow = row;
            }
        }

        if (bestRow == null) return List.of();

        Elements imgs = bestRow.select("div._hmag7l.tooltipped img");
        List<String> urls = new ArrayList<>();
        for (Element img : imgs) {
            String src = img.hasAttr("data-src") ? img.attr("data-src") : img.attr("src");
            if (src.contains("/item/")) urls.add(src);
        }
        return urls.stream().distinct().collect(Collectors.toList());
    }

    /* ===================== Helpers ===================== */

    private static int parseItemIdFromTooltip(String tooltip) {
        try {
            int idx = tooltip.lastIndexOf('-');
            return (idx != -1) ? Integer.parseInt(tooltip.substring(idx + 1).trim()) : -1;
        } catch (Exception e) { return -1; }
    }

    private static int parseItemIdFromSrc(String src) {
        try {
            int slash = src.lastIndexOf('/');
            int dot = src.lastIndexOf(".png");
            if (slash != -1 && dot != -1 && dot > slash) {
                return Integer.parseInt(src.substring(slash + 1, dot));
            }
            return -1;
        } catch (Exception e) { return -1; }
    }

    private static String firstNonEmpty(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return null;
    }

    private static String toAbs(String u) {
        if (u.startsWith("http")) return u;
        if (u.startsWith("//"))   return "https:" + u;
        if (u.startsWith("/"))    return "https://ddragon.leagueoflegends.com" + u;
        return u;
    }
}