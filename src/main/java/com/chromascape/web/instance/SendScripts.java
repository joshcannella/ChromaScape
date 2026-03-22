package com.chromascape.web.instance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller responsible for providing available script names.
 *
 * <p>This controller scans the {@code scripts} directory for available script files and returns
 * their names to the client.
 */
@RestController
@RequestMapping("/api")
public class SendScripts {

  /** The directory where script classes are located. */
  private static final Path SCRIPTS_DIR = Paths.get("src/main/java/com/chromascape/scripts");

  /**
   * Returns a list of script entries with file name and version.
   *
   * @return list of maps with "name" and "version" keys
   * @throws IOException if an I/O error occurs while reading the directory
   */
  @GetMapping("/scripts")
  public List<Map<String, String>> getScripts() throws IOException {
    try (Stream<Path> stream = Files.walk(SCRIPTS_DIR)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith("Script.java"))
          .map(SCRIPTS_DIR::relativize)
          .map(path -> path.toString().replace("\\", "/"))
          .sorted()
          .map(name -> Map.of("name", name, "version", readVersion(name)))
          .collect(Collectors.toList());
    }
  }

  private static String readVersion(String fileName) {
    String className =
        "com.chromascape.scripts." + fileName.replace(".java", "").replace("/", ".");
    try {
      return (String) Class.forName(className).getField("VERSION").get(null);
    } catch (Exception e) {
      return "";
    }
  }
}
