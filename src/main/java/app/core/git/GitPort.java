package app.core.git;

import java.util.List;

public interface GitPort {
  String getHeadCommit();

  List<String> listTrackedFiles();

  byte[] readWorkingTreeFile(String repoRelativePath);
}
