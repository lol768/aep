import sbt.Tracked.{inputChanged, outputChanged}
import sbt._
import sbt.util.FileInfo.lastModified

object Changes {
  /**
    Monitors inputDir and outputDir, tracking the state in cacheDir and
    running fn if there are any changes
  */
  def ifChanged(cacheDir: File, inputDir: File, outputDir: File)(fn: => Unit): Unit = {
    val cachedTask = inputChanged(cacheDir / "inputs") { (inChanged, in: FilesInfo[ModifiedFileInfo]) =>
        outputChanged(cacheDir / "output") { (outChanged, outputs: FilesInfo[ModifiedFileInfo]) =>
          if (inChanged || outChanged) {
            fn
          }
        }
      }
    cachedTask(lastModified(inputDir.allPaths.get.toSet))(() => lastModified(outputDir.allPaths.get.toSet))
  }
}
