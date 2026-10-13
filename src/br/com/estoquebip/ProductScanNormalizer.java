package br.com.estoquebip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Port of Rotina360 assets/js/modules/product-scan-domain.js (R192/R527).
 * Candidate order matters. Never silently choose among multiple catalog matches.
 * This class does not access any Rotina360 operational table.
 */
final class ProductScanNormalizer {
 private ProductScanNormalizer() {}

 static String clean(String value) {
  return value == null ? "" : value.replaceAll("[\\u200B-\\u200D\\uFEFF]", "")
    .trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
 }

 static List<String> candidates(String value) {
  String code = clean(value);
  if (code.isEmpty()) return Collections.emptyList();
  if (!code.matches("[0-9]+"))
   return Collections.singletonList(code);
  List<String> cores = new ArrayList<>();
  if (code.startsWith("000")) cores.add(code.substring(3));
  else if (code.startsWith("00")) cores.add(code.substring(2));
  cores.add(code);
  Set<String> direct = new LinkedHashSet<>();
  for (String core : cores) {
   if (core.matches("^(10|11)[0-9]{6}.*")) direct.add(core.substring(0, 8));
   if (core.matches("^(260|360|460)[0-9]{8}.*")) direct.add(core.substring(0, 11));
   if (core.matches("^[234][0-9]{10}.*")) direct.add(core.substring(0, 11));
   if (core.matches("^60[0-9]{8}.*")) {
    String body = core.substring(0, 10);
    for (String prefix : new String[]{"2","3","4"}) direct.add(prefix + body);
   }
   if (!direct.isEmpty()) return new ArrayList<>(direct);
  }
  Set<String> embedded = new LinkedHashSet<>();
  for (String core : cores) {
   for (int i = 0; i < core.length(); i++) {
    String tail = core.substring(i);
    if (tail.matches("^(260|360|460)[0-9]{8}.*")) {
     embedded.add(tail.substring(0, 11));
     continue;
    }
    char previous = i == 0 ? '\\0' : core.charAt(i - 1);
    if (tail.matches("^60[0-9]{8}.*") && previous != '2' && previous != '3' && previous != '4') {
     String body = tail.substring(0, 10);
     for (String prefix : new String[]{"2","3","4"}) embedded.add(prefix + body);
     continue;
    }
    if (tail.matches("^(10|11)[0-9]{6}.*")) embedded.add(tail.substring(0, 8));
   }
  }
  if (!embedded.isEmpty()) return new ArrayList<>(embedded);
  String core = cores.get(0);
  if (core.matches("^[234].*") && core.length() >= 11) return Collections.singletonList(core.substring(0, 11));
  if (core.startsWith("1") && core.length() >= 8) return Collections.singletonList(core.substring(0, 8));
  return Collections.singletonList(core);
 }

 static String normalize(String value) {
  List<String> matches = candidates(value);
  return matches.isEmpty() ? "" : matches.get(0);
 }
}
