package com.tinylabproductions.uploader.utils

import java.io.{IOException, RandomAccessFile}
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}

import com.tinylabproductions.uploader._
import net.sf.sevenzipjbinding._
import net.sf.sevenzipjbinding.impl.{OutItemFactory, RandomAccessFileInStream, RandomAccessFileOutStream}

object SevenZipUtils {
  sealed trait Item {
    def archivePath: Path
  }

  case class ArchiveFile(sourcePath: Path, archivePath: Path) extends Item {
    lazy val content = Files.readAllBytes(sourcePath)
  }
  case class ArchiveDir(archivePath: Path) extends Item

  def pack(_items: Vector[Item], zipFilePath: Path, format: CompressionFormat): Unit = {
    val (items, tempFiles) = format match {
      case tf: CompressionFormat.TarFirst =>
        val path = tf.tarPath(zipFilePath)
        pack(_items, path, CompressionFormat.Tar)
        (Vector(ArchiveFile(path, path.getFileName)), Vector(path))
      case _ => (_items, Vector.empty)
    }

    var inStreams = Vector.empty[RandomAccessFileInStream]
    val callback = new IOutCreateCallback[IOutItemAllFormats] {
      override def setOperationResult(operationResultOk: Boolean) = {}
      override def setCompleted(complete: Long) = {}
      override def setTotal(total: Long) = {}

      override def getStream(index: Int) = items(index) match {
        case af: ArchiveFile =>
          val stream = new RandomAccessFileInStream(new RandomAccessFile(af.sourcePath.toFile, "r"))
          inStreams :+= stream
          stream
        case _ => null
      }

      override def getItemInformation(index: Int, outItemFactory: OutItemFactory[IOutItemAllFormats]) = {
        val outItem = outItemFactory.createOutItem()
        val item = items(index)
        item match {
          case file: ArchiveFile => outItem.setDataSize(file.content.length.toLong)
          case dir: ArchiveDir => outItem.setPropertyIsDir(true)
        }
        outItem.setPropertyPath(item.archivePath.toUnixPathStr)
        outItem
      }
    }

    val stream = new RandomAccessFileOutStream(new RandomAccessFile(zipFilePath.toFile, "rw"))
    val outArchive = format.createArchive()
    try {
      outArchive.createArchive(stream, items.size, callback)
    }
    catch {
      case e: SevenZipException =>
        e.printStackTraceExtended()
        throw e
    }
    finally {
      inStreams.foreach(_.close())
      outArchive.close()
      stream.close()

      tempFiles.foreach(Files.delete)
    }
  }

  def pack(folder: Path, zipFilePath: Path, format: CompressionFormat) {
    var items = Vector.empty[Item]

    Files.walkFileTree(folder, new SimpleFileVisitor[Path]() {
      @throws(classOf[IOException])
      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        items :+= ArchiveFile(file, folder.relativize(file))
        FileVisitResult.CONTINUE
      }

      @throws(classOf[IOException])
      override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = {
        if (dir ne folder) items :+= ArchiveDir(folder.relativize(dir))
        FileVisitResult.CONTINUE
      }
    })

    pack(items, zipFilePath, format)
  }
}
