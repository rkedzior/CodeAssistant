package app.core.git;

import java.util.List;

public interface GitPort {
  String getHeadCommit();

  List<String> listTrackedFiles();

  byte[] readWorkingTreeFile(String repoRelativePath);

  List<String> listTrackedFilesAtCommit(String commit);

  byte[] readFileAtCommit(String commit, String repoRelativePath);
}
