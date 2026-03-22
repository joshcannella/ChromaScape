package com.chromascape.web.instance;

import com.chromascape.scripts.ScriptVersion;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
 * their names and git-derived versions to the client.
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
          .map(name -> Map.of("name", name, "version", buildVersion(name)))
          .collect(Collectors.toList());
    }
  }

  /** Combines the script's @ScriptVersion annotation with the git short hash. */
  private static String buildVersion(String fileName) {
    String hash = gitHash(SCRIPTS_DIR.resolve(fileName));
    String semver = readSemver(fileName);
    if (semver.isEmpty()) {
      return hash;
    }
    return hash.isEmpty() ? semver : semver + "." + hash;
  }

  /** Reads @ScriptVersion(major, minor) from the script class. */
  private static String readSemver(String fileName) {
    String className =
        "com.chromascape.scripts." + fileName.replace(".java", "").replace("/", ".");
    try {
      Class<?> clazz = Class.forName(className);
      ScriptVersion sv = clazz.getAnnotation(ScriptVersion.class);
      return sv != null ? sv.major() + "." + sv.minor() : "";
    } catch (Exception e) {
      return "";
    }
  }

  /** Returns the short git commit hash of the last commit that touched the given file. */
  private static String gitHash(Path file) {
    try {
      Process p = new ProcessBuilder("git", "log", "-1", "--format=%h", file.toString())
          .redirectErrorStream(true)
          .start();
      try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
        String hash = r.readLine();
        p.waitFor();
        return hash != null && !hash.isEmpty() ? hash : "";
      }
    } catch (Exception e) {
      return "";
    }
  }
}
