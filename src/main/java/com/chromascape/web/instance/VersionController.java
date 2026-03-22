package com.chromascape.web.instance;

import java.util.Map;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes build version info to the frontend. */
@RestController
@RequestMapping("/api")
public class VersionController {

  private final BuildProperties build;

  public VersionController(BuildProperties build) {
    this.build = build;
  }

  @GetMapping("/version")
  public Map<String, String> getVersion() {
    return Map.of("version", build.getVersion(), "time", build.getTime().toString());
  }
}
