package com.tinylabproductions.uploader.utils

import java.io.{OutputStream, FileOutputStream, IOException}
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{SimpleFileVisitor, Path, FileVisitResult, Files}
import java.util.zip.{ZipEntry, ZipOutputStream}
import com.tinylabproductions.uploader._

object ZipUtils {
  def pack(folder: Path, os: OutputStream) {
    val zos = new ZipOutputStream(os)
    try {
      Files.walkFileTree(folder, new SimpleFileVisitor[Path]() {
        @throws(classOf[IOException])
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          zos.putNextEntry(new ZipEntry(folder.relativize(file).toUnixPathStr))
          Files.copy(file, zos)
          zos.closeEntry()
          FileVisitResult.CONTINUE
        }

        @throws(classOf[IOException])
        override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = {
          if (dir ne folder) {
            zos.putNextEntry(new ZipEntry(s"${folder.relativize(dir).toUnixPathStr}/"))
            zos.closeEntry()
          }
          FileVisitResult.CONTINUE
        }
      })
    }
    finally {
      zos.close()
    }
  }

  def pack(folder: Path, zipFilePath: Path) {
    pack(folder, new FileOutputStream(zipFilePath.toFile))
  }
}
