package helpers

import java.io.File

import com.google.common.io.{ByteSource, Files}

object FileResourceUtils {
  def byteSourceResource(name: String): ByteSource = Files.asByteSource(new File(getClass.getResource(name).getFile))

  def fileLookupFailed = throw new Exception("Your file doesn't exist mate")
}
