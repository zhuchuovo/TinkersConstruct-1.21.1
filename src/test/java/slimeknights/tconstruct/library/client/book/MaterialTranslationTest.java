package slimeknights.tconstruct.library.client.book;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialTranslationTest {
  @Test
  void chineseContainsAllEnglishMaterialKeys() throws IOException {
    JsonObject english = readLanguage("en_us");
    JsonObject chinese = readLanguage("zh_cn");
    Set<String> missing = new TreeSet<>();

    for (String key : english.keySet()) {
      if (key.startsWith("material.") && !chinese.has(key)) {
        missing.add(key);
      }
    }

    assertThat(missing).as("material translation keys missing from zh_cn").isEmpty();
  }

  private static JsonObject readLanguage(String language) throws IOException {
    String path = "assets/tconstruct/lang/" + language + ".json";
    InputStream stream = Objects.requireNonNull(
      MaterialTranslationTest.class.getClassLoader().getResourceAsStream(path),
      "Missing language file: " + path
    );
    try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      return JsonParser.parseReader(reader).getAsJsonObject();
    }
  }
}
