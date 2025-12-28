package app.platform.delivery.web;

import app.core.git.GitPort;
import app.core.projectconfig.ProjectConfigPort;
import app.core.projectstate.ProjectMetadataState;
import app.core.projectstate.ProjectStatePort;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class FileViewerController {
  private final ProjectConfigPort projectConfigPort;
  private final ProjectStatePort projectStatePort;
  private final GitPort gitPort;

  public FileViewerController(
      ProjectConfigPort projectConfigPort, ProjectStatePort projectStatePort, GitPort gitPort) {
    this.projectConfigPort = projectConfigPort;
    this.projectStatePort = projectStatePort;
    this.gitPort = gitPort;
  }

  @GetMapping("/file")
  public String viewFile(@RequestParam(name = "path", required = false) String path, Model model) {
    boolean configured = projectConfigPort.load().isPresent();
    model.addAttribute("configured", configured);

    ProjectMetadataState metadataState = projectStatePort.getOrCreateMetadata();
    String lastIndexedCommit = metadataState.metadata().lastIndexedCommit();
    model.addAttribute(
        "lastIndexedCommitDisplay",
        lastIndexedCommit == null || lastIndexedCommit.isBlank() ? "Not indexed" : lastIndexedCommit);

    model.addAttribute("path", path == null ? "" : path);
    model.addAttribute("content", null);
    model.addAttribute("error", null);

    if (!configured) {
      model.addAttribute("error", "Project is not configured yet.");
      return "file";
    }

    if (path == null || path.isBlank()) {
      model.addAttribute("error", "Missing required query parameter: path");
      return "file";
    }

    String normalizedPath;
    try {
      normalizedPath = normalizeRepoRelativePath(path);
    } catch (IllegalArgumentException e) {
      model.addAttribute("error", e.getMessage());
      return "file";
    }

    model.addAttribute("path", normalizedPath);

    Set<String> trackedFiles = new HashSet<>(gitPort.listTrackedFiles());
    if (!trackedFiles.contains(normalizedPath)) {
      model.addAttribute("error", "File is not tracked: " + normalizedPath);
      return "file";
    }

    byte[] bytes;
    try {
      bytes = gitPort.readWorkingTreeFile(normalizedPath);
    } catch (RuntimeException e) {
      model.addAttribute("error", "Failed to read file: " + normalizedPath);
      return "file";
    }

    if (looksBinary(bytes)) {
      model.addAttribute("error", "Cannot display binary file: " + normalizedPath);
      return "file";
    }

    try {
      model.addAttribute("content", decodeUtf8(bytes));
    } catch (CharacterCodingException e) {
      model.addAttribute("error", "Cannot decode file as UTF-8: " + normalizedPath);
    }

    return "file";
  }

  private static String normalizeRepoRelativePath(String rawPath) {
    String path = rawPath.trim().replace('\\', '/');
    while (path.startsWith("./")) {
      path = path.substring(2);
    }

    if (path.isBlank()) {
      throw new IllegalArgumentException("Path must be non-blank.");
    }
    if (path.startsWith("/") || path.startsWith("~") || path.startsWith("\\\\")) {
      throw new IllegalArgumentException("Path must be repo-relative.");
    }
    if (path.contains(":")) {
      throw new IllegalArgumentException("Path must be repo-relative.");
    }
    if (path.indexOf('\u0000') >= 0) {
      throw new IllegalArgumentException("Path contains invalid characters.");
    }

    String[] parts = path.split("/", -1);
    for (String part : parts) {
      if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
        throw new IllegalArgumentException("Path is not allowed: " + rawPath);
      }
    }

    return path;
  }

  private static boolean looksBinary(byte[] bytes) {
    for (byte b : bytes) {
      if (b == 0) return true;
    }
    return false;
  }

  private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
    CharsetDecoder decoder =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
    CharBuffer decoded = decoder.decode(ByteBuffer.wrap(bytes));
    return decoded.toString();
  }
}
