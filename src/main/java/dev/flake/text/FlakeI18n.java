package dev.flake.text;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Shared 3-language localization service (uk_ua, ru_ru, en_us) for both server/common logic,
 * client UI, and headless JUnit tests.
 */
public final class FlakeI18n {
	public static final String UK_UA = "uk_ua";
	public static final String RU_RU = "ru_ru";
	public static final String EN_US = "en_us";
	public static final List<String> SUPPORTED_LANGUAGES = List.of(UK_UA, RU_RU, EN_US);

	private static final Map<String, Map<String, String>> TABLES = new LinkedHashMap<>();
	private static volatile Supplier<String> languageSupplier = () -> UK_UA;

	static {
		for (String lang : SUPPORTED_LANGUAGES) {
			TABLES.put(lang, loadLangTable(lang));
		}
	}

	private FlakeI18n() {}

	private static Map<String, String> loadLangTable(String lang) {
		String path = "/assets/flake/lang/" + lang + ".json";
		Map<String, String> map = new LinkedHashMap<>();
		try (InputStream in = FlakeI18n.class.getResourceAsStream(path)) {
			if (in == null) return Collections.emptyMap();
			JsonObject obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
				if (entry.getValue().isJsonPrimitive()) {
					map.put(entry.getKey(), entry.getValue().getAsString());
				}
			}
		} catch (Exception e) {
			throw new IllegalStateException("Failed to load language file: " + path, e);
		}
		return Collections.unmodifiableMap(map);
	}

	public static void setLanguageSupplier(Supplier<String> supplier) {
		languageSupplier = supplier != null ? supplier : () -> UK_UA;
	}

	public static String normalizeLang(String raw) {
		if (raw == null || raw.isBlank()) return UK_UA;
		String lower = raw.trim().toLowerCase(Locale.ROOT);
		if (lower.startsWith("uk")) return UK_UA;
		if (lower.startsWith("ru") || lower.startsWith("be")) return RU_RU;
		if (lower.startsWith("en")) return EN_US;
		return EN_US;
	}

	public static String currentLang() {
		try {
			return normalizeLang(languageSupplier.get());
		} catch ( Exception ignored) {
			return UK_UA;
		}
	}

	public static String tr(String key) {
		return trForLang(currentLang(), key);
	}

	public static String tr(String key, Object... args) {
		String template = tr(key);
		if (args == null || args.length == 0) return template;
		return String.format(Locale.ROOT, template, args);
	}

	public static String trForLang(String lang, String key) {
		if (key == null) return "";
		String norm = normalizeLang(lang);
		Map<String, String> primary = TABLES.getOrDefault(norm, Collections.emptyMap());
		String val = primary.get(key);
		if (val != null) return val;
		Map<String, String> fallbackUk = TABLES.getOrDefault(UK_UA, Collections.emptyMap());
		val = fallbackUk.get(key);
		if (val != null) return val;
		Map<String, String> fallbackEn = TABLES.getOrDefault(EN_US, Collections.emptyMap());
		return fallbackEn.getOrDefault(key, key);
	}

	public static List<String> allTranslations(String key) {
		List<String> result = new ArrayList<>(SUPPORTED_LANGUAGES.size());
		for (String lang : SUPPORTED_LANGUAGES) {
			String val = TABLES.getOrDefault(lang, Collections.emptyMap()).get(key);
			if (val != null && !result.contains(val)) result.add(val);
		}
		return result;
	}

	public static boolean isTranslationOf(String key, String candidate) {
		if (candidate == null) return false;
		for (String val : allTranslations(key)) {
			if (val.equalsIgnoreCase(candidate.trim())) return true;
		}
		return false;
	}

	public static Set<String> keysForLang(String lang) {
		return TABLES.getOrDefault(normalizeLang(lang), Collections.emptyMap()).keySet();
	}
}
